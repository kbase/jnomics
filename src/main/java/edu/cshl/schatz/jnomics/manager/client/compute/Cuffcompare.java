package edu.cshl.schatz.jnomics.manager.client.compute;

import edu.cshl.schatz.jnomics.manager.api.JnomicsThriftFileStatus;
import edu.cshl.schatz.jnomics.manager.api.JnomicsThriftJobID;
import edu.cshl.schatz.jnomics.manager.client.Utility;
import edu.cshl.schatz.jnomics.manager.client.ann.Flag;
import edu.cshl.schatz.jnomics.manager.client.ann.FunctionDescription;
import edu.cshl.schatz.jnomics.manager.client.ann.Parameter;
import edu.cshl.schatz.jnomics.manager.common.KBaseIDTranslator;
import edu.cshl.schatz.jnomics.manager.client.fs.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * User: Sri
 */

@FunctionDescription(description = "Cuffcompare "+
		"Compare transcript Assemblies\n"+
        "Compare each assembly with the transcriptome \n"+
        "Reference gtf can be specified with the -ref_gtf flag. " + 
        "Input and Output must reside on the Cluster's filesystem. \n"+
        "Optional additonal arguments may be supplied to \n"+
        "cuffcompare. These options are passed as a string to cuffcompare"+
        " and should include hyphens(-) if necessary.\n"
)
public class Cuffcompare extends ComputeBase {

    @Flag(shortForm = "-h",longForm = "--help")
    public boolean help;
    
    @Parameter(shortForm = "-in", longForm = "--input", description = "input (Multiple transcript files(.gtf),comma seperated)")
    public String in;
    
    @Parameter(shortForm = "-out", longForm= "--output", description = "output (directory)")
    public String out;
    
    @Parameter(shortForm = "-ref_gtf", longForm= "--ref_gtf", description = "reference gtf")
    public String ref_gtf;
    
    @Parameter(shortForm = "-assembly_opts", longForm = "--assembly_options", description = "options to Cuffcompare (optional)")
    public String assembly_opts;
    
    @Parameter(shortForm = "-working_dir", longForm = "--working_dir", description = "workingdir (optional)" )
    public String working_dir;
    
    @Override
    public void handle(List<String> remainingArgs,Properties properties) throws Exception {

        super.handle(remainingArgs,properties);
        if(help){
            System.out.println(Utility.helpFromParameters(this.getClass()));
            return;
        }else if(null == in){
            System.out.println("missing -in parameter");
        }else if(null == out){
            System.out.println("missing -out parameter");
        }else if(null == ref_gtf){
            System.out.println("missing -ref_gtf parameter");
        }else if(!fsclient.checkFileStatus(ref_gtf, auth)){
    		System.out.println("ERROR : " + ref_gtf + "does'nt exist");
        }else if(fsclient.checkFileStatus(out, auth)){
    		System.out.println("ERROR : Output directory already exists");
    	}else{
    		List<String> input = Arrays.asList(in.split(","));
            for(String file : input) {
            	if(!fsclient.checkFileStatus(file, auth)){
            		System.out.println("ERROR : " + file + " does'nt exist");
            		return;
            	}
            }
            JnomicsThriftJobID jobID = client.callCuffcompare(
                    in,
                    out,
                    ref_gtf,
                    Utility.nullToString(assembly_opts),
                    Utility.nullToString(working_dir),
                    auth);
            System.out.println("Submitted Job: " + jobID.getJob_id());
            return;
        }
        System.out.println(Utility.helpFromParameters(this.getClass()));
    }
}
