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


@FunctionDescription(description = "Cuffmerge Transcript assembler\n"+
        "Merges the transcript assemblies to a single transcriptome.\n"+
        "Organism can be specified with the -ref flag. Input and \n"+
        "Output must reside on the Cluster's filesystem. \n"+
        "Optional additonal arguments may be supplied to \n"+
        "cuffmerge. These options are passed as a string to cuffmerge "+ 
        "and should include hyphens(-)if necessary.\n"
)
public class Cuffmerge extends ComputeBase {

    @Flag(shortForm = "-h",longForm = "--help")
    public boolean help;
    
    @Parameter(shortForm = "-in", longForm = "--input", description = "input (.gtf files, comma seperated)")
    public String in;
    
    @Parameter(shortForm = "-ref", longForm = "--reference genome", description="reference genome(.fa)")
    public String organism;
    
    @Parameter(shortForm = "-out", longForm= "--output", description = "output (directory)")
    public String out;
    
    @Parameter(shortForm = "-assembly_opts", longForm = "--assembly_options", description = "options to pass to Tophat (optional)")
    public String assembly_opts;
    
    @Parameter(shortForm = "-gtf_opts", longForm = "--GTF file", description = "Gene model annotation file(.gtf) (optional)")
    public String gtf_opts;
    
    @Parameter(shortForm = "-working_dir", longForm = "--working_dir", description = "workingdir (optional)")
    public String working_dir;
    
    @Override
    public void handle(List<String> remainingArgs,Properties properties) throws Exception {

        super.handle(remainingArgs,properties);
        if(help){
            System.out.println(Utility.helpFromParameters(this.getClass()));
            return;
        }else if(null == organism){
            System.out.println("missing -ref parameter");
        }else if(null == in){
            System.out.println("missing -in parameter");
        }else if(null == out){
            System.out.println("missing -out parameter");
        }else if(fsclient.checkFileStatus(out, auth)){
    		System.out.println("ERROR : Output directory already exists");
        }else{
        	boolean check = false;
            String clean_org = KBaseIDTranslator.translate(organism);
            List<String> genomes = fsclient.listGenomes(auth);
            List<String> input = Arrays.asList(in.split(","));
            for(String file : input) {
            	if(!fsclient.checkFileStatus(file, auth)){
            		System.out.println("ERROR : " + file + " does'nt exist");
            		return;
            	}
            }
            for(String genome : genomes){
            	if(genome.equals(organism)){
            		check = true;
            	}
            }
            if(!check){
            	System.out.println("ERROR : " + organism + " does'nt exist in the repository");
            	System.out.println("try jk-compute-list-genomes to list the supported genomes");
            		return;
            }
            JnomicsThriftJobID jobID = client.callCuffmerge(
                    in,
                    clean_org,
                    out,
                    Utility.nullToString(assembly_opts),
                    Utility.nullToString(gtf_opts),
                    Utility.nullToString(working_dir),
                    auth);
            System.out.println("Submitted Job: " + jobID.getJob_id());
            return;
        }

        System.out.println(Utility.helpFromParameters(this.getClass()));
    }
}
