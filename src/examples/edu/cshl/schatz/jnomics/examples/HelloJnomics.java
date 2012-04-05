package edu.cshl.schatz.jnomics.examples;

import java.io.IOException;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import edu.cshl.schatz.jnomics.mapreduce.JnomicsReducerO;
import edu.cshl.schatz.jnomics.mapreduce.JnomicsTool;
import edu.cshl.schatz.jnomics.ob.writable.QueryTemplate;

/**
 * HelloJnomics example
 * <p>
 * Counts the number of sequencing reads that span a query template (refer to
 * Samtools documentation for the definition of query template) Jnomics is input
 * file agnostic. It will pull the query template from SAM, fastq etc. Note
 * however, that "query template" may have a different meaning in each file
 * context.
 * 
 * @author James
 */
public class HelloJnomics extends JnomicsTool {

    public static void main(String[] args) throws Exception {
        JnomicsTool.run(new HelloJnomics(), args);
    }

    @Override
    public int run(String[] args) throws Exception {
        Job job = getJob();

        job.setReducerClass(HelloJnomicsReducerO.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(QueryTemplate.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        return job.waitForCompletion(true) ? 0 : 1;
    }

    /**
     * Count the number of reads within a QueryTemplate QueryTemplate refers to
     * the Samtools definition of a query template
     * 
     * @author James
     */
    public static class HelloJnomicsReducerO
            extends JnomicsReducerO<Writable, QueryTemplate, Text, IntWritable> {
    	
    	final IntWritable outCount = new IntWritable();
    	final Text outText = new Text();

        @Override
        protected void reduce(Writable key, Iterable<QueryTemplate> values, Context cxt)
                throws IOException, InterruptedException {
            
        	for (final QueryTemplate template : values) {
        		outCount.set(template.size());
                outText.set(template.getTemplateName());
                cxt.write(outText, outCount);
            }
        }
    }
}