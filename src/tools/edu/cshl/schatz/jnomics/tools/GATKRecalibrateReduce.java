package edu.cshl.schatz.jnomics.tools;

import edu.cshl.schatz.jnomics.cli.JnomicsArgument;
import edu.cshl.schatz.jnomics.io.ThreadedStreamConnector;
import edu.cshl.schatz.jnomics.ob.SAMRecordWritable;
import edu.cshl.schatz.jnomics.util.ProcessUtil;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapreduce.Partitioner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * User: james
 **/
public class GATKRecalibrateReduce extends GATKBaseReduce<SamtoolsMap.SamtoolsKey, SAMRecordWritable, SAMRecordWritable, NullWritable> {

    private final JnomicsArgument recal_covar_arg = new JnomicsArgument("recal_file","<recal.covar> recalibration file", true);
    private final SAMRecordWritable recordWritable = new SAMRecordWritable(); 
    
    private File recal;
    
    @Override
    public Class getOutputKeyClass() {
        return SAMRecordWritable.class;
    }

    @Override
    public Class getOutputValueClass() {
        return NullWritable.class;
    }

    @Override
    public Class<? extends Partitioner> getPartitionerClass() {
        return SamtoolsReduce.SamtoolsPartitioner.class;
    }

    @Override
    public Class<? extends WritableComparator> getGrouperClass() {
        return SamtoolsReduce.SamtoolsGrouper.class;
    }

    @Override
    public JnomicsArgument[] getArgs() {
        JnomicsArgument[] args = super.getArgs();
        JnomicsArgument[] newArgs = new JnomicsArgument[args.length+1];
        newArgs[0] = recal_covar_arg;
        System.arraycopy(args,0,newArgs,1,args.length);
        return newArgs;
    }
    
    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        super.setup(context);
        recal = binaries.get(recal_covar_arg.getName());
    }

    @Override
    protected void reduce(SamtoolsMap.SamtoolsKey key, Iterable<SAMRecordWritable> values, Context context)
            throws IOException, InterruptedException {

        File tmpBam = new File(context.getTaskAttemptID()+".tmp.bam");
        /**Write bam to temp file**/
        String samtools_convert_cmd = String.format("%s view -Sb -o %s -", samtools_binary, tmpBam);
        System.out.println(samtools_convert_cmd);
        Process samtools_convert = Runtime.getRuntime().exec(samtools_convert_cmd);

        Thread errConn = new Thread(new ThreadedStreamConnector(samtools_convert.getErrorStream(),System.err));
        Thread outConn = new Thread(new ThreadedStreamConnector(samtools_convert.getInputStream(),System.out));
        outConn.start();errConn.start();

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(samtools_convert.getOutputStream()));
        long count = 0;
        for(SAMRecordWritable record: values){
            if(0 == count){
                writer.write(record.getTextHeader()+"\n");
            }
            writer.write(record+"\n");
            if(0 == ++count % 1000 ){
                context.progress();
            }
        }
        writer.close();
        samtools_convert.waitFor();
        outConn.join();errConn.join();

        /**Index Bam**/
        String samtools_idx_cmd = String.format("%s index %s", samtools_binary, tmpBam);
        ProcessUtil.exceptionOnError(ProcessUtil.execAndReconnect(samtools_idx_cmd));

        File recalibratedBam = new File(context.getTaskAttemptID()+".recal.bam");

        String recal_cmd = String.format("java -Xmx4g -jar %s -T TableRecalibration -R %s -I %s -recalFile %s -o %s",
                gatk_binary, reference_fa,tmpBam,recal,recalibratedBam);
        ProcessUtil.exceptionOnError(ProcessUtil.execAndReconnect(recal_cmd));
        tmpBam.delete();

        SAMFileReader reader = new SAMFileReader(recalibratedBam,true);
        reader.setValidationStringency(SAMFileReader.ValidationStringency.LENIENT);
        for(SAMRecord record: reader){
            recordWritable.set(record);
            context.write(recordWritable,NullWritable.get());
        }

        recalibratedBam.delete();
    }
}
