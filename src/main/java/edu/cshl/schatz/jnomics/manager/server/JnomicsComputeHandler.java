package edu.cshl.schatz.jnomics.manager.server;

import edu.cshl.schatz.jnomics.manager.api.*;
import edu.cshl.schatz.jnomics.manager.common.*;
import edu.cshl.schatz.jnomics.manager.common.IDServer;
import edu.cshl.schatz.jnomics.mapreduce.JnomicsJobBuilder;
import edu.cshl.schatz.jnomics.tools.*;
import edu.cshl.schatz.jnomics.grid.*;
import edu.cshl.schatz.jnomics.util.TextUtil;

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.JobStatus;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.thrift.TException;
import org.ggf.drmaa.DrmaaException;
import org.slf4j.LoggerFactory;

import us.kbase.auth.AuthToken;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;




import us.kbase.shock.client.ShockNodeId;



//import java.io.File;
import java.io.*;
//import java.io.FileNotFoundException;
//import java.io.FileOutputStream;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.OutputStream;
//import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * User: james
 */
public class JnomicsComputeHandler implements JnomicsCompute.Iface{

	private final org.slf4j.Logger logger = LoggerFactory.getLogger(JnomicsComputeHandler.class);

	private Properties properties;

	private static final int NUM_REDUCE_TASKS = 1024;

	private JnomicsServiceAuthentication authenticator;


	public JnomicsComputeHandler(Properties systemProperties){
		properties = systemProperties;
		authenticator = new JnomicsServiceAuthentication();
	}

	private Configuration getGenericConf(){
		Configuration conf = new Configuration();

		//if you don't give Path's it will not load the files
		conf.addResource(new Path(properties.getProperty("core-site-xml")));
		conf.addResource(new Path(properties.getProperty("mapred-site-xml")));
		conf.addResource(new Path(properties.getProperty("hdfs-site-xml")));
		conf.set("fs.default.name", properties.getProperty("hdfs-default-name"));
		conf.set("mapred.jar", properties.getProperty("jnomics-jar-path"));
		conf.set("grid-script-path", properties.getProperty("grid-script-path"));
		conf.set("grid-job-slots", properties.getProperty("grid-job-slots"));
		//	conf.set("shock-url", properties.getProperty("shock-url"));
		return conf;
	}


	@Override
	public JnomicsThriftJobID alignBowtie(String inPath, String organism, String outPath, String opts, Authentication auth)
			throws TException, JnomicsThriftException {
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}

		logger.info("Starting Bowtie2 process for user " + username);
		JnomicsJobBuilder builder = new JnomicsJobBuilder(getGenericConf(),Bowtie2Map.class);
		builder.setInputPath(inPath)
		.setOutputPath(outPath)
		.setParam("bowtie_binary","bowtie/bowtie2-align")    
		.setParam("bowtie_index", "btarchive/"+organism+".fa")
		.setParam("bowtie_opts",opts)
		.setJobName(username+"-bowtie2-"+inPath)
		.addArchive(properties.getProperty("hdfs-index-repo")+"/"+organism+"_bowtie.tar.gz#btarchive")
		.addArchive(properties.getProperty("hdfs-index-repo") + "/bowtie.tar.gz#bowtie");

		Configuration conf = null;
		try{
			conf = builder.getJobConf();
		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}
		return launchJobAs(username,conf);
	}

	@Override
	public JnomicsThriftJobID alignBWA(String inPath, String organism, String outPath, 
			String alignOpts, String sampeOpts, Authentication auth)
					throws TException, JnomicsThriftException {
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		logger.info("Starting Bwa process for user " + username);

		JnomicsJobBuilder builder = new JnomicsJobBuilder(getGenericConf(), BWAMap.class);
		builder.setInputPath(inPath)
		.setOutputPath(outPath)
		.setParam("bwa_binary","bwa/bwa")
		.setParam("bwa_index", "bwaarchive/"+organism+".fa")
		.setParam("bwa_align_opts",alignOpts)
		.setParam("bwa_sampe_opts",sampeOpts)
		.setJobName(username+"-bwa-"+inPath)
		.addArchive(properties.getProperty("hdfs-index-repo")+"/"+organism+"_bwa.tar.gz#bwaarchive")
		.addArchive(properties.getProperty("hdfs-index-repo")+"/bwa.tar.gz#bwa");

		Configuration conf = null;
		try{
			conf = builder.getJobConf();
		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}
		return launchJobAs(username,conf);
	}
	
	public JnomicsThriftJobID fastqtoPe(String file1 ,String file2,String outPath, String workingdir, Authentication auth)throws TException, JnomicsThriftException{	
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		String uuid = UUID.randomUUID().toString();
		String jobname =username+"-fastqtopege-"+uuid;
		//String jobname =username+"-tophat-"+inPath.substring(inPath.lastIndexOf('/') + 1).replaceAll("[./,]", "_");

		Configuration conf = null;
		OutputStream out = null;
		String jobid = null;	


		JnomicsGridJobBuilder builder = new JnomicsGridJobBuilder(getGenericConf());
		builder.setOutputPath(outPath)
		.setParam("fastq1",file1)
		.setParam("fastq2",file2)
		.setJobName(jobname);
		logger.info("conf properties are set");

		try{
			conf = builder.getJobConf();
			out = new FileOutputStream(System.getProperty("user.home")+ "/" + jobname +".xml");
			conf.writeXml(out);
			builder.LaunchGridJob(conf);
			jobid = conf.get("grid_jobId");
			//scontact = conf.get("grid_session");
			//sout = new FileOutputStream(System.getProperty("user.home")+ "/" + jobid +".xml");
			//sout.write(scontact.getBytes(),0,scontact.length());
		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}finally{
			try {
				out.close();
				//	sout.close();
			} catch (Exception e) {
				throw new JnomicsThriftException(e.toString());
			}
		}
		return new JnomicsThriftJobID(conf.get("grid_jobId"));	
	}

	public JnomicsThriftJobID alignTophat(String ref_genome,String inPath ,String gtffile,String outPath, String alignOpts, String workingdir, Authentication auth)throws TException, JnomicsThriftException{	
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		String uuid = UUID.randomUUID().toString();
		String jobname =username+"-tophat-"+uuid;
		//String jobname =username+"-tophat-"+inPath.substring(inPath.lastIndexOf('/') + 1).replaceAll("[./,]", "_");
		String tophatbinary =  properties.getProperty("hdfs-index-repo")+"/tophat_v2.tar.gz";
		String refGenome = properties.getProperty("hdfs-index-repo")+"/"+ref_genome+"_bowtie.tar.gz";
		String tophatopts = alignOpts.replaceAll(","," ").toString();

		Configuration conf = null;
		OutputStream out = null;
		String jobid = null;	
		logger.info("tophat_binary is "+ tophatbinary );
		logger.info("jobname - " + jobname);
		logger.info("alignopts - " + tophatopts);

		JnomicsGridJobBuilder builder = new JnomicsGridJobBuilder(getGenericConf());
		builder.setInputPath(inPath)
		.setOutputPath(outPath)
		.setParam("tophat_align_opts",tophatopts)
		.setParam("calling_function","edu.cshl.schatz.jnomics.tools.GridJobMain")
		.setParam("grid_working_dir", workingdir)
		.setJobName(jobname)
		.setParam("tophat_ref_genome",refGenome)
		.setParam("tophat_gtf",gtffile)
		.setParam("tophat_binary",tophatbinary);
		logger.info("conf properties are set");

		try{
			conf = builder.getJobConf();
			out = new FileOutputStream(System.getProperty("user.home")+ "/" + jobname +".xml");
			conf.writeXml(out);
			builder.LaunchGridJob(conf);
			jobid = conf.get("grid_jobId");
			//scontact = conf.get("grid_session");
			//sout = new FileOutputStream(System.getProperty("user.home")+ "/" + jobid +".xml");
			//sout.write(scontact.getBytes(),0,scontact.length());
		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}finally{
			try {
				out.close();
				//	sout.close();
			} catch (Exception e) {
				throw new JnomicsThriftException(e.toString());
			}
		}
		return new JnomicsThriftJobID(conf.get("grid_jobId"));	
	}

	public JnomicsThriftJobID callCufflinks(String inPath, String outPath,String ref_gtf,
			String alignOpts, String workingdir,Authentication auth)
					throws TException, JnomicsThriftException {
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		String uuid = UUID.randomUUID().toString();
		String jobname = username+"-cufflinks-"+uuid;
		String cufflinks_binary =  properties.getProperty("hdfs-index-repo")+"/cufflinks_v2.tar.gz";
		String cuffopts = alignOpts.replaceAll(","," ").toString();
		Configuration conf = null;
		OutputStream out = null;

		logger.info("cufflinks_binary -" + cufflinks_binary);
		logger.info("jobname - " + jobname);
		logger.info("outpath - " + outPath);
		logger.info("alignOpts - " + cuffopts);
		logger.info("working dir -  " + workingdir);

		JnomicsGridJobBuilder builder = new JnomicsGridJobBuilder(getGenericConf());
		builder.setInputPath(inPath)
		.setOutputPath(outPath)
		.setParam("cufflinks_gtf", ref_gtf)
		.setParam("cufflinks_opts",cuffopts)
		.setParam("calling_function","edu.cshl.schatz.jnomics.tools.GridJobMain")
		.setParam("grid_working_dir", workingdir)
		.setJobName(jobname)
		.setParam("cufflinks_binary",cufflinks_binary);
		logger.info("conf properties are set");
		try{
			conf = builder.getJobConf();
			out = new FileOutputStream(System.getProperty("user.home")+ "/" + jobname +".xml");
			conf.writeXml(out);
			builder.LaunchGridJob(conf);
		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}finally{
			try {
				out.close();
			} catch (Exception e) {
				throw new JnomicsThriftException(e.toString());
			}
		}
		return new JnomicsThriftJobID(conf.get("grid_jobId"));	
	}

	public JnomicsThriftJobID callCuffmerge(String inPath,String ref_genome, String outPath, 
			String alignOpts, String gtf_file, String workingdir, Authentication auth)
					throws TException, JnomicsThriftException {
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		String uuid = UUID.randomUUID().toString();
		String cufflinks_binary =  properties.getProperty("hdfs-index-repo")+"/cufflinks_v2.tar.gz";
		String jobname = username+"-cuffmerge-"+uuid;
		String refGenome = properties.getProperty("hdfs-index-repo")+"/"+ref_genome+"_bowtie.tar.gz"; 
		String cuffopts = alignOpts.replaceAll(","," ").toString();
		Path filenamepath = new Path("cuffmerge-"+jobname+".txt");

		logger.info("cufflinks_binary -" + cufflinks_binary );
		logger.info("Reference is -" + refGenome ) ;
		logger.info("jobname - " + jobname);
		logger.info("outpath - " + outPath);
		logger.info("alignOpts - " + cuffopts);
		logger.info("working dir - " + workingdir);

		Configuration conf = null;
		OutputStream out = null;
		FileSystem infs = null;
		FSDataOutputStream outStream = null;
		try{
			infs = JnomicsFileSystem.getFileSystem(properties, username);
			List<String> input = Arrays.asList(inPath.split(","));
			outStream = infs.create(filenamepath);
			for(String filename : input){
				outStream.writeBytes(filename+"\n");
			}

		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}finally{
			try {
				outStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		JnomicsGridJobBuilder builder = new JnomicsGridJobBuilder(getGenericConf());
		builder.setInputPath(filenamepath.getName())
		.setOutputPath(outPath)
		.setParam("cuffmerge_opts",cuffopts)
		.setParam("cuffmerge_gtf", gtf_file)
		.setParam("calling_function","edu.cshl.schatz.jnomics.tools.GridJobMain")
		.setParam("grid_working_dir", workingdir)
		.setJobName(jobname)
		.setParam("cuffmerge_ref_genome",refGenome)
		.setParam("cufflinks_binary",cufflinks_binary);

		logger.info("conf properties are set");
		try{
			conf = builder.getJobConf();
			out = new FileOutputStream(System.getProperty("user.home")+ "/" + jobname +".xml");
			conf.writeXml(out);
			builder.LaunchGridJob(conf);
		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}finally{
			try {
				out.close();
				//				inout.close();
			} catch (Exception e) {
				throw new JnomicsThriftException(e.toString());
			}
		}
		return new JnomicsThriftJobID(conf.get("grid_jobId"));	
	}

	public JnomicsThriftJobID callCuffdiff(String infiles, String outPath, String ref_genome,
			String assemblyOpts, String condn_labels, String merged_gtf,String withReplicates, String workingdir, Authentication auth)
					throws TException, JnomicsThriftException {
		String username;

		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		String uuid = UUID.randomUUID().toString();
		String cufflinks_binary =  properties.getProperty("hdfs-index-repo")+"/cufflinks_v2.tar.gz";
		String jobname = username+"-cuffdiff-"+uuid;
		String refGenome = properties.getProperty("hdfs-index-repo")+"/"+ref_genome+"_bowtie.tar.gz"; 
		String cuffopts = assemblyOpts.replaceAll(","," ").toString();
		
		logger.info("cufflinks_binary - " + cufflinks_binary);
		logger.info("Reference is -" + refGenome ) ;
		logger.info("jobname - " + jobname);
		logger.info("outpath - " + outPath);
		logger.info("assemblyOpts - " + cuffopts);
		logger.info("condn_labels - " + condn_labels);
		logger.info("merged_gtf - " + merged_gtf);
		logger.info("withReplicates - " +  withReplicates);
		logger.info("working dir - " + workingdir);

		Configuration conf = null;
		OutputStream out = null;

		JnomicsGridJobBuilder builder = new JnomicsGridJobBuilder(getGenericConf());
		builder.setParam("input_files", infiles)
		.setOutputPath(outPath)
		.setParam("cuffdiff_opts",assemblyOpts)
		.setParam("cuffdiff_condn_labels", condn_labels)
		.setParam("calling_function","edu.cshl.schatz.jnomics.tools.GridJobMain")
		.setParam("grid_working_dir", workingdir)
		.setJobName(jobname)
		.setParam("cuffdiff_merged_gtf",merged_gtf)
		.setParam("cuffdiff_ref_genome",refGenome)
		.setParam("withReplicates", withReplicates)
		.setParam("cufflinks_binary",cufflinks_binary);

		try{
			conf = builder.getJobConf();
			out = new FileOutputStream(System.getProperty("user.home")+ "/" + jobname +".xml");
			conf.writeXml(out);
			builder.LaunchGridJob(conf);
		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}finally{
			try {
				out.close();
			} catch (Exception e) {
				throw new JnomicsThriftException(e.toString());
			}
		}
		return new JnomicsThriftJobID(conf.get("grid_jobId"));	
	}

	public JnomicsThriftJobID callCuffcompare(String inPath, String outPath, 
			String gtf_file, String alignOpts, String workingdir, Authentication auth)
					throws TException, JnomicsThriftException {
		String username;

		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		String uuid = UUID.randomUUID().toString();
		String jobname = username+"-cuffcompare-"+uuid;
		String cufflinks_binary = properties.getProperty("hdfs-index-repo")+"/cufflinks_v2.tar.gz";
		Path filenamepath = new Path("cuffcompare-"+jobname+".txt");
		String cuffopts = alignOpts.replaceAll(","," ").toString();

		logger.info("cufflinks_binary is" + cufflinks_binary);
		logger.info("jobname - " + jobname);
		logger.info("outpath is " + outPath);
		logger.info("alignOpts " + cuffopts);
		logger.info("working dir is " + workingdir);

		Configuration conf = null;
		OutputStream out = null;	
		FileSystem infs = null;
		FSDataOutputStream outStream = null;
		try{
			infs = JnomicsFileSystem.getFileSystem(properties, username);
			List<String> input = Arrays.asList(inPath.split(","));
			outStream = infs.create(filenamepath);
			for(String filename : input){
				outStream.writeBytes(filename+"\n");
			}

		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}finally{
			try {
				outStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		logger.info("cufflinks_binary is" + properties.getProperty("hdfs-index-repo")+"/cufflinks_v2.tar.gz");
		JnomicsGridJobBuilder builder = new JnomicsGridJobBuilder(getGenericConf());
		builder.setInputPath(filenamepath.getName())
		.setOutputPath(outPath)
		.setParam("cuffcompare_opts",cuffopts)
		.setParam("cuffcompare_gtf", gtf_file)
		.setParam("calling_function","edu.cshl.schatz.jnomics.tools.GridJobMain")
		.setParam("grid_working_dir", workingdir)
		.setJobName(jobname)
		.setParam("cufflinks_binary",cufflinks_binary);
		logger.info("Conf properties are set");

		try{
			conf = builder.getJobConf();
			out = new FileOutputStream(System.getProperty("user.home")+ "/" + jobname +".xml");
			conf.writeXml(out);
			builder.LaunchGridJob(conf);
		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}finally{
			try {
				out.close();
			} catch (Exception e) {
				throw new JnomicsThriftException(e.toString());
			}
		}
		return new JnomicsThriftJobID(conf.get("grid_jobId"));	
	}

	@Override
	public JnomicsThriftJobID ShockRead(String shockNodeID, String hdfsPath, Authentication auth) throws TException , JnomicsThriftException{	
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		AuthToken oauth;
		FileSystem fs = null;
		Configuration conf = null;
		OutputStream out = null;

		logger.info("Inside shock client read" );
		String uuid = UUID.randomUUID().toString();
		String jobname = username+"-read-"+uuid;
		byte[] btoken = auth.token.getBytes();
		String etoken = Base64.encodeBase64String(btoken);		
		JnomicsGridJobBuilder builder = new JnomicsGridJobBuilder(getGenericConf());
		builder.setInputPath(shockNodeID)
		.setOutputPath(hdfsPath)
		.setJobName(jobname)
		.setParam("shock-url",properties.getProperty("shock-url"))
		.setParam("shock-token",etoken)
		.setParam("http-proxy",properties.getProperty("http-proxy"));
		logger.info("Conf properties are set");
		try{
			conf = builder.getJobConf();
			out = new FileOutputStream(System.getProperty("user.home")+ "/" + jobname +".xml");
			conf.writeXml(out);
			builder.LaunchGridJob(conf);
		} catch (Exception e) {
			e.printStackTrace();
			throw new JnomicsThriftException(e.toString());
		}finally{
			try {
				out.close();
			} catch (Exception e) {
				throw new JnomicsThriftException(e.toString());
			}
		}
		return new JnomicsThriftJobID(conf.get("grid_jobId"));
	}

	@Override
	public JnomicsThriftJobID ShockWrite(String filename, String hdfsPath, Authentication auth) throws TException , JnomicsThriftException{	
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		logger.info("Opening file: " + hdfsPath + " for user: " + username);
		Configuration conf = null;
		OutputStream out = null;
		String uuid = UUID.randomUUID().toString();
		String jobname = username+"-write-"+uuid;
		byte[] btoken = auth.token.getBytes();
		String etoken = Base64.encodeBase64String(btoken);		
		JnomicsGridJobBuilder builder = new JnomicsGridJobBuilder(getGenericConf());
		builder.setInputPath(hdfsPath)
		.setJobName(jobname)
		.setParam("shock-url",properties.getProperty("shock-url"))
		.setParam("shock-token",etoken)
		.setParam("http-proxy",properties.getProperty("http-proxy"));
		logger.info("Conf properties are set");
		try{
			conf = builder.getJobConf();
			out = new FileOutputStream(System.getProperty("user.home")+ "/" + jobname +".xml");
			conf.writeXml(out);
			builder.LaunchGridJob(conf);
		}catch(Exception e){
			logger.error(e.toString());
			throw new JnomicsThriftException(e.getMessage());
		}finally{
			try {
				out.close();
			} catch (Exception e) {
				throw new JnomicsThriftException(e.toString());
			}
		}
		return new JnomicsThriftJobID(conf.get("grid_jobId"));	
	}

	public JnomicsThriftJobID workspaceUpload(String filename,String genome_id,String desc ,String title,String srcDate, String onto_term_id, 
			String onto_term_def, String onto_term_name, String seq_type, String shockid,String src_id, String workingdir,
			Authentication auth) throws TException , JnomicsThriftException{	
		String username;
		String bedtools_binary =  properties.getProperty("hdfs-index-repo")+"/bedtools_2.17.0.tar.gz";
		String bedtools_script = properties.getProperty("bedtools-script-path");
		String shock_url = properties.getProperty("shock-url")+"node/"+shockid;
	
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		
		Configuration conf = null;
		OutputStream out = null;
		String uuid = UUID.randomUUID().toString();
		String jobname = username+"-wsupload-"+uuid;
		String kbaseid = null;
		byte[] btoken = auth.token.getBytes();
		String etoken = Base64.encodeBase64String(btoken);
		String prefix ="kb|sample";
		String ext_src_id =  src_id.replace(" ", "::")+"::"+"kb|"+genome_id;
//		String keyvalue = username+"_"+prefix+"_"+genome_id+"_"+shockid;
		String keyvalue = ext_src_id+"___"+ext_src_id.split("::")[1];
		logger.info("key : "+keyvalue );
		try {
//			int ret_id = IDServer.registerId();
			kbaseid = IDServer.registerId(prefix,keyvalue); ///"sample." + ret_id;
		}catch( Exception e) {
			e.toString();
		}
		JnomicsGridJobBuilder builder = new JnomicsGridJobBuilder(getGenericConf());
		builder.setInputPath(filename)
		.setJobName(jobname)
		.setParam("cdmi-url", properties.getProperty("cdmi-url"))
		.setParam("workspace-url", properties.getProperty("workspace-url"))
		.setParam("bedtools_binary",bedtools_binary)
		.setParam("bedtools_script",bedtools_script)
		.setParam("auth-token", etoken)
		.setParam("kb_id",kbaseid)
		.setParam("genome_id",genome_id)
		.setParam("description",desc)
		.setParam("title",title)
		.setParam("ext_src_date",srcDate)
		.setParam("ext_src_id", ext_src_id)
		.setParam("onto_term_id", onto_term_id)
		.setParam("onto_term_def", onto_term_def)
		.setParam("onto_term_name", onto_term_name)
		.setParam("sequence_type", seq_type)
		.setParam("shock_url",shock_url)
		.setParam("grid_working_dir", workingdir);
		logger.info("Conf properties are set");
		try{
			conf = builder.getJobConf();
			out = new FileOutputStream(System.getProperty("user.home")+ "/" + jobname +".xml");
			conf.writeXml(out);
			builder.LaunchGridJob(conf);
		}catch(Exception e){
			logger.error(e.toString());
			throw new JnomicsThriftException(e.getMessage());
		}finally{
			try {
				out.close();
			} catch (Exception e) {
				throw new JnomicsThriftException(e.toString());
			}
		}
		return new JnomicsThriftJobID(conf.get("grid_jobId"));	
	}
	public JnomicsThriftJobID ShockBatchWrite(List<String> inPath ,String outPath, Authentication auth)throws TException, JnomicsThriftException{	
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		FileSystem fs = null;
		Path filenamePath;
		Configuration conf = null;
		try {
			fs = JnomicsFileSystem.getFileSystem(properties, username);
			URI hdfspath = fs.getUri();
			filenamePath = new Path(hdfspath+"/user/"+ username + "/cpyfiles.txt"); 
			if(fs.exists(filenamePath)){
				fs.delete(filenamePath);
			}
			FSDataOutputStream outStream = fs.create(filenamePath);
			for(String filename : inPath){
				outStream.writeBytes(filename+"\n");
			}
			outStream.close();
			JnomicsJobBuilder builder = new JnomicsJobBuilder(getGenericConf(),ShockLoad.class);
			builder.setInputPath(filenamePath.toString())
			.setOutputPath(outPath);
			conf = builder.getJobConf();
		}catch (Exception e){
			throw new JnomicsThriftException(e.toString());
		} finally{
			try {
				JnomicsFileSystem.closeFileSystem(fs);
			} catch (Exception e) {
				throw new JnomicsThriftException(e.toString());
			}
		}
		return launchJobAs(username, conf);	
	}
	public JnomicsThriftJobID ReadHDFSdir(List<String> inPath ,String outPath, Authentication auth)throws TException, JnomicsThriftException{	
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		FileSystem fs = null;
		Path filenamePath;
		Configuration conf = null;
		try {
			fs = JnomicsFileSystem.getFileSystem(properties, username);
			URI hdfspath = fs.getUri();
			filenamePath = new Path(hdfspath+"/user/"+ username + "/cpyhdfsfiles.txt"); 
			if(fs.exists(filenamePath)){
				fs.delete(filenamePath);
			}
			FSDataOutputStream outStream = fs.create(filenamePath);
			for(String filename : inPath){
				outStream.writeBytes(filename+"\n");
			}
			outStream.close();
			JnomicsJobBuilder builder = new JnomicsJobBuilder(getGenericConf(),ShockLoad.class);
			builder.setInputPath(filenamePath.toString())
			.setOutputPath(outPath);
			conf = builder.getJobConf();
		}catch (Exception e){
			throw new JnomicsThriftException(e.toString());
		} finally{
			try {
				JnomicsFileSystem.closeFileSystem(fs);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return launchJobAs(username, conf);	
	}

	@Override
	public JnomicsThriftJobID snpSamtools(String inPath, String organism, String outPath, Authentication auth) throws TException, JnomicsThriftException {
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		logger.info("Running samtools pipeline for user: "+ username);

		JnomicsJobBuilder builder = new JnomicsJobBuilder(getGenericConf(), SamtoolsMap.class, SamtoolsReduce.class);
		builder.setInputPath(inPath)
		.setOutputPath(outPath)
		.addArchive(properties.getProperty("hdfs-index-repo")+"/"+organism+"_samtools.tar.gz#starchive")
		.addArchive(properties.getProperty("hdfs-index-repo")+"/samtools.tar.gz#samtools")
		.addArchive(properties.getProperty("hdfs-index-repo")+"/bcftools.tar.gz#bcftools")
		.setParam("samtools_binary","samtools/samtools")
		.setParam("bcftools_binary","bcftools/bcftools")
		.setParam("reference_fa","starchive/"+organism+".fa")
		.setParam("genome_binsize","1000000")
		.setReduceTasks(NUM_REDUCE_TASKS)
		.setJobName(username+"-snp-"+inPath);

		Configuration conf = null;
		try{
			conf = builder.getJobConf();
		}catch (Exception e){
			throw new JnomicsThriftException(e.toString());
		}
		return launchJobAs(username, conf);
	}

	@Override
	public JnomicsThriftJobStatus getJobStatus(final JnomicsThriftJobID jobID, final Authentication auth)
			throws TException, JnomicsThriftException {
		final String username = authenticator.authenticate(auth);
		if(null == username){
			throw new JnomicsThriftException("Permission Denied");
		}
		logger.info("Getting job status for user "+ username);

		return new JobClientRunner<JnomicsThriftJobStatus>(username,
				new Configuration(),properties){
			@Override
			public JnomicsThriftJobStatus jobClientTask() throws Exception {

				RunningJob job = getJobClient().getJob(JobID.forName(jobID.getJob_id()));
				return new JnomicsThriftJobStatus(job.getID().toString(),
						username,
						null,
						job.isComplete(),
						job.getJobState(),
						0,
						null,
						job.mapProgress(),
						job.reduceProgress());
			}
		}.run();
	}
	@Override
	public String getGridJobStatus(final JnomicsThriftJobID jobID,final Authentication auth) throws JnomicsThriftException{
		final String username = authenticator.authenticate(auth);
		if(null == username){
			throw new JnomicsThriftException("Permission Denied");
		}
		logger.info("Getting job status for user "+ username);
		JnomicsGridJobBuilder builder = new JnomicsGridJobBuilder(getGenericConf());
		String status = null;
		try {
			status = builder.getjobstatus(jobID.getJob_id());
		} catch (Exception e) {
			throw new JnomicsThriftException(e.toString());
		}
		return status ;

	}
	@Override
	public List<JnomicsThriftJobStatus> getAllJobs(Authentication auth) throws JnomicsThriftException, TException {
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		logger.info("Getting all job status for user "+ username);

		JobStatus[] statuses = new JobClientRunner<JobStatus[]>(username,new Configuration(),properties){
			@Override
			public JobStatus[] jobClientTask() throws Exception {
				logger.info("getting jobs");
				return getJobClient().getAllJobs();
			}
		}.run();
		logger.info("got jobs");
		List<JnomicsThriftJobStatus> newStats = new ArrayList<JnomicsThriftJobStatus>();
		for(JobStatus stat: statuses){
			if(0 == username.compareTo(stat.getUsername()))
				newStats.add(new JnomicsThriftJobStatus(stat.getJobID().toString(),
						stat.getUsername(),
						stat.getFailureInfo(),
						stat.isJobComplete(),
						stat.getRunState(),
						stat.getStartTime(),
						stat.getJobPriority().toString(),
						stat.mapProgress(),
						stat.reduceProgress()));
		}
		return newStats;
	}


	/**
	 * Writes a manifest file in a directory called manifests in home directory
	 *
	 * @param filename filename to use as prefix for manifest file
	 * @param data data to write to manifest file, each string in the array is aline
	 * @param username to perform fs operations as
	 * @return Path of the created manfiest file
	 * @throws JnomicsThriftException
	 */
	private Path writeManifest(String filename, String []data, String username) throws JnomicsThriftException{

		//write manifest file and run job
		FileSystem fs = null;
		try {
			fs = FileSystem.get(new URI(properties.getProperty("hdfs-default-name")),
					new Configuration(),username);
			if(!fs.exists(new Path("manifests"))){
				fs.mkdirs(new Path("manifests"));
			}
		}catch (Exception e) {
			try{
				fs.close();
			}catch (Exception t){
				throw new JnomicsThriftException(t.toString());
			}
			throw new JnomicsThriftException(e.toString());
		}

		Path manifest,f1;
		FSDataOutputStream outStream = null;
		try{
			f1 = new Path(filename);
			manifest = new Path("manifests/"+f1.getName()+"-"+UUID.randomUUID().toString()+".manifest");
			outStream = fs.create(manifest);
			for(String line: data){
				outStream.write((line + "\n").getBytes());
			}
		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}finally{
			try {
				outStream.close();
			} catch (IOException e) {
				throw new JnomicsThriftException();
			}finally{
				try{
					fs.close();
				}catch(Exception b){
					throw new JnomicsThriftException(b.toString());
				}
			}
		}
		return manifest;
	}


	@Override
	public JnomicsThriftJobID pairReads(String file1, String file2, String outFile, Authentication auth)
			throws JnomicsThriftException, TException {
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		logger.info("Pairing reads in hdfs for user "+ username);


		String data = TextUtil.join("\t",new String[]{file1,file2,outFile});
		Path manifest = writeManifest(new File(file1).getName(), new String[]{data}, username);

		Path manifestlog = new Path(manifest +".log");
		JnomicsJobBuilder builder = new JnomicsJobBuilder(getGenericConf(),PELoaderMap.class,PELoaderReduce.class);
		builder.setJobName(new File(file1).getName()+"-pe-conversion")
		.setInputPath(manifest.toString())
		.setOutputPath(manifestlog.toString())
		.setParam("mapreduce.outputformat.class","org.apache.hadoop.mapreduce.lib.output.TextOutputFormat")
		.setReduceTasks(1);
		Configuration conf;
		try{
			conf = builder.getJobConf();
		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}
		JnomicsThriftJobID id = launchJobAs(username,conf);
		logger.info("submitted job: " + conf.get("mapred.job.name") + " " + id);
		return new JnomicsThriftJobID(id);
	}

	@Override
	public JnomicsThriftJobID singleReads(String file, String outFile, Authentication auth)
			throws JnomicsThriftException, TException {

		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		logger.info("converting single reads to sequence file for user "+ username);


		String fileBase = new File(file).getName();

		String data = TextUtil.join("\t",new String[]{file,outFile});
		Path manifest = writeManifest(fileBase,new String[]{data},username);

		Path manifestlog = new Path(manifest + ".log");

		JnomicsJobBuilder builder = new JnomicsJobBuilder(getGenericConf(),SELoaderMap.class,SELoaderReduce.class);
		builder.setJobName(fileBase+"-pe-conversion")
		.setInputPath(manifest.toString())
		.setOutputPath(manifestlog.toString())
		.setReduceTasks(1);

		Configuration conf = null;
		try{
			conf = builder.getJobConf();
		}catch(Exception e){
			throw new JnomicsThriftException(e.toString());
		}

		JnomicsThriftJobID id = launchJobAs(username,conf);
		logger.info("submitted job: " + conf.get("mapred.job.name") + " " + id);
		return new JnomicsThriftJobID(id);        

	}

	public JnomicsThriftJobID launchJobAs(String username, final Configuration conf)
			throws JnomicsThriftException {
		RunningJob runningJob = new JobClientRunner<RunningJob>(username,conf,properties){
			@Override
			public RunningJob jobClientTask() throws Exception {
				return getJobClient().submitJob(getJobConf());
			}
		}.run();
		String jobid = runningJob.getID().toString();
		logger.info("submitted job: " + conf.get("mapred.job.name") + " " + jobid);
		return new JnomicsThriftJobID(jobid);
	}

	//    public JnomicsThriftJobID launchGridJobAs(String username, final Configuration conf)
	//            throws JnomicsThriftException {
	//        RunningJob runningJob = new JobClientRunner<RunningJob>(username,conf,properties){
	//            @Override
	//            public RunningJob jobClientTask() throws Exception {
	//                return getJobClient().submitJob(getJobConf());
	//            }
	//        }.run();
	//        String jobid = runningJob.getID().toString();
	//        logger.info("submitted job: " + conf.get("mapred.job.name") + " " + jobid);
	//        return new JnomicsThriftJobID(jobid);
	//    }
	//    
	@Override
	public boolean mergeVCF(String inDir, String inAlignments, String outVCF, Authentication auth)
			throws JnomicsThriftException, TException {
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}

		final Configuration conf = getGenericConf();
		logger.info("Merging VCF: " + inDir + ":" + inAlignments + ":" + outVCF + " for user " + username);

		final Path in = new Path(inDir);
		final Path alignments = new Path(inAlignments);
		final Path out = new Path(outVCF);
		boolean status  = false;
		try {
			status = UserGroupInformation.createRemoteUser(username).doAs(new PrivilegedExceptionAction<Boolean>() {
				@Override
				public Boolean run() throws Exception {
					VCFMerge.merge(in,alignments,out,conf);
					return true;
				}
			});
		}catch (Exception e) {
			logger.info("Failed to merge: " + e.toString());
			throw new JnomicsThriftException(e.toString());
		}
		return status;
	}

	@Override
	public boolean mergeCovariate(String inDir, String outCov, Authentication auth) throws JnomicsThriftException, TException {
		String username;
		if(null == (username = authenticator.authenticate(auth))){
			throw new JnomicsThriftException("Permission Denied");
		}
		logger.info("Merging covariates for user"+ username);

		final Configuration conf = getGenericConf();
		final Path in = new Path(inDir);
		final Path out = new Path(outCov);

		boolean status = false;
		try{
			status = UserGroupInformation.createRemoteUser(username).doAs(new PrivilegedExceptionAction<Boolean>() {
				@Override
				public Boolean run() throws Exception {
					CovariateMerge.merge(in, out, conf);
					return true;
				}
			});
		}catch(Exception e){
			logger.info("Failed to merge:" + e.toString());
			throw new JnomicsThriftException(e.toString());
		}
		return status;
	}

	private JnomicsJobBuilder getGATKConfBuilder(String inPath, String outPath, String organism){
		return null;
		/**FIXME**/
		/*JnomicsJobBuilder builder = new JnomicsJobBuilder(getGenericConf(),SamtoolsMap.class);
        builder.setParam("samtools_binary","gatk/samtools")
                .setParam("reference_fa","gatk/"+organism+".fa")
                .setParam("gatk_jar", "gatk/GenomeAnalysisTK.jar")
                .setParam("genome_binsize","1000000")
                .setReduceTasks(NUM_REDUCE_TASKS)
                .addArchive(properties.getProperty("hdfs-index-repo")+"/"+organism+"_gatk.tar.gz#gatk")
                .setInputPath(inPath)
                .setOutputPath(outPath);
                return builder;*/
	}

	@Override
	public JnomicsThriftJobID gatkRealign(String inPath, String organism, String outPath, Authentication auth)
			throws JnomicsThriftException, TException {
		return null;
		/**FIXME**/
		/**String username;
        if(null == (username = authenticator.authenticate(auth))){
            throw new JnomicsThriftException("Permission Denied");
        }
        logger.info("gatk realign for user "+ username);

        JnomicsJobBuilder builder = getGATKConfBuilder(inPath, outPath, organism);
        builder.setReducerClass(GATKRealignReduce.class)
                .setJobName(username+"-gatk-realign-"+inPath);
        Configuration conf = null;
        try{
            conf = builder.getJobConf();
        }catch(Exception e){
            throw new JnomicsThriftException(e.toString());
        }
        return launchJobAs(username, conf);*/
	}

	@Override
	public JnomicsThriftJobID gatkCallVariants(String inPath, String organism, String outPath, Authentication auth)
			throws JnomicsThriftException, TException {
		return null;
		/*String username;
        if(null == (username = authenticator.authenticate(auth))){
            throw new JnomicsThriftException("Permission Denied");
        }
        logger.info("gatkCallVariants for user "+ username);

        JnomicsJobBuilder builder = getGATKConfBuilder(inPath,outPath,organism);
        builder.setReducerClass(GATKCallVarReduce.class)
                .setJobName(username+"-gatk-call-variants");
        Configuration conf = null;
        try{
            conf = builder.getJobConf();
        }catch(Exception e){
            throw new JnomicsThriftException(e.toString());
        }
        return launchJobAs(username, conf);*/
	}

	@Override
	public JnomicsThriftJobID gatkCountCovariates(String inPath, String organism, String vcfMask,
			String outPath, Authentication auth)
					throws JnomicsThriftException, TException {
		return null;
		/*String username;
        if(null == (username = authenticator.authenticate(auth))){
            throw new JnomicsThriftException("Permission Denied");
        }
        logger.info("gatkCountCovariates for user "+ username);

        JnomicsJobBuilder builder = getGATKConfBuilder(inPath, outPath, organism);
        Path vcfMaskPath = new Path(vcfMask);
        builder.setReducerClass(GATKCountCovariatesReduce.class)
                .setParam("mared.cache.files",vcfMaskPath.toString()+"#"+vcfMaskPath.getName())
                .setParam("tmpfiles",vcfMaskPath.toString()+"#"+vcfMaskPath.getName())
                .setParam("vcf_mask",vcfMaskPath.getName())
                .setJobName(username+"-gatk-count-covariates");
        Configuration conf = null;
        try{
            conf = builder.getJobConf();
        }catch(Exception e){
            throw new JnomicsThriftException(e.toString());
        }
        return launchJobAs(username,conf);*/
	}

	@Override
	public JnomicsThriftJobID gatkRecalibrate(String inPath, String organism, String recalFile, String outPath, Authentication auth)
			throws JnomicsThriftException, TException {
		return null;
		/*String username;
        if(null == (username = authenticator.authenticate(auth))){
            throw new JnomicsThriftException("Permission Denied");
        }
        logger.info("gatkRecalibrate for user "+ username);

        JnomicsJobBuilder builder = getGATKConfBuilder(inPath, outPath, organism);
        Path recalFilePath = new Path(recalFile);
        builder.setReducerClass(GATKRecalibrateReduce.class)
                .setParam("mapred.cache.files", recalFilePath.toString()+"#"+recalFilePath.getName())
                .setParam("tmpfiles", recalFilePath.toString()+"#"+recalFilePath.getName())
                .setParam("recal_file",recalFilePath.getName())
                .setJobName(username+"-gatk-recalibrate");
        Configuration conf = null;
        try{
            conf = builder.getJobConf();
        }catch(Exception e){
            throw new JnomicsThriftException(e.toString());
        }
        return launchJobAs(username,conf);*/
	}

	@Override
	public JnomicsThriftJobID runSNPPipeline(final String inPath, final String organism, final String outPath,
			final Authentication auth)
					throws JnomicsThriftException, TException {
		final String username = authenticator.authenticate(auth);
		if( null == username ){
			throw new JnomicsThriftException("Permission Denied");
		}
		logger.info("Running snpPipeline for user "+ username);
		String initPath = inPath;
		boolean loadShock = false;
		if(inPath.startsWith("http")){
			String []shockPaths = inPath.split(",");
			String []manifestData = new String[shockPaths.length];
			int i=0;
			for(String p: shockPaths){
				manifestData[i++] = p + "\t" + new Path(new Path(outPath,"http_load"),"d"+i).toString();
			}
			Path manifest = writeManifest("shockdata",manifestData,username);
			initPath = manifest.toString();
			loadShock = true;
		}

		logger.info("Starting new Thread");

		final String nxtPath = initPath;
		final boolean loadShockfinal = loadShock;

		new Thread(new Runnable(){
			@Override
			public void run() {
				String alignIn;
				if(loadShockfinal){
					final JnomicsJobBuilder shockBuilder = new JnomicsJobBuilder(getGenericConf(),HttpLoaderMap.class,
							HttpLoaderReduce.class);
					shockBuilder.setInputPath(nxtPath)
					.setOutputPath(nxtPath+"out")
					.setJobName("http-load"+nxtPath)
					.setReduceTasks(10);
					String proxy;
					if(null != (proxy = properties.getProperty("http-proxy",null)))
						shockBuilder.setParam("proxy",proxy);
					Job shockLoadJob = null;
					try{
						shockLoadJob = UserGroupInformation.createRemoteUser(username).doAs(new PrivilegedExceptionAction<Job>() {
							@Override
							public Job run() throws Exception {
								Job job = new Job(shockBuilder.getJobConf());
								job.waitForCompletion(true);
								return job;
							}
						});
					}catch(Exception e){
						logger.error("Failed to load data:" + e.toString());
						return;
					}
					try{
						if(null == shockLoadJob || !shockLoadJob.isSuccessful()){
							logger.error("Failed to load data from shock");
							return;
						}
					}catch(Exception e){
						logger.error(e.getMessage());
						return;
					}
					alignIn = new Path(outPath,"http_load").toString();
				}else{
					alignIn = inPath;
				}

				Path alignOut = new Path(outPath,"bowtie_align");

				final JnomicsJobBuilder alignBuilder = new JnomicsJobBuilder(getGenericConf(),Bowtie2Map.class);
				alignBuilder.setInputPath(alignIn)
				.setOutputPath(alignOut.toString())
				.setParam("bowtie_binary", "bowtie/bowtie2-align")
				.setParam("bowtie_index", "btarchive/"+organism+".fa")
				.setJobName(username + "-bowtie2-" + inPath)
				.addArchive(properties.getProperty("hdfs-index-repo") + "/" + organism + "_bowtie.tar.gz#btarchive")
				.addArchive(properties.getProperty("hdfs-index-repo") + "/bowtie.tar.gz#bowtie");
				Job j = null;
				try{
					j = UserGroupInformation.createRemoteUser(username).doAs(new PrivilegedExceptionAction<Job>() {
						@Override
						public Job run() throws Exception {
							Job job = new Job(alignBuilder.getJobConf());
							job.waitForCompletion(true);
							return job;
						}
					});
				}catch(Exception e){
					logger.error("Failed to align:" + e.toString());
					return;
				}

				logger.info("Alignment task finished");

				Path snpIn = alignOut;
				Path snpOut = new Path(outPath, "snp");
				Job snpJob = null;
				try {
					if(null != j && j.isSuccessful()){
						logger.info("alignment successful, running samtools");
						final JnomicsJobBuilder snpBuilder = new JnomicsJobBuilder(getGenericConf(), SamtoolsMap.class, SamtoolsReduce.class);
						snpBuilder.setInputPath(snpIn.toString())
						.setOutputPath(snpOut.toString())
						.addArchive(properties.getProperty("hdfs-index-repo")+"/"+organism+"_samtools.tar.gz#starchive")
						.addArchive(properties.getProperty("hdfs-index-repo")+"/samtools.tar.gz#samtools")
						.addArchive(properties.getProperty("hdfs-index-repo")+"/bcftools.tar.gz#bcftools")
						.setParam("samtools_binary","samtools/samtools")
						.setParam("bcftools_binary","bcftools/bcftools")
						.setParam("reference_fa","starchive/"+organism+".fa")
						.setParam("genome_binsize","1000000")
						.setReduceTasks(NUM_REDUCE_TASKS)
						.setJobName(username+"-snp-"+inPath);

						snpJob = UserGroupInformation.createRemoteUser(username).doAs(new PrivilegedExceptionAction<Job>() {
							@Override
							public Job run() throws Exception {
								Job job = new Job(snpBuilder.getJobConf());
								job.waitForCompletion(true);
								return job;
							}
						});
					}
				} catch (Exception e) {
					logger.error("Failed to call snps" + e.toString());
					return;
				}

				try{
					if(null != snpJob && snpJob.isSuccessful()){
						mergeVCF(snpOut.toString(),alignOut.toString(),new Path(outPath,"out.vcf").toString(),auth);
					}
				}catch(Exception e){
					logger.error("Problem merging covariates");
					return;
				}
			}
		}).start();
		return new JnomicsThriftJobID();
	}
}
