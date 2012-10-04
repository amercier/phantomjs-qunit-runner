package net.kennychua.phantomjs_qunit_runner;

/*
 * This is not the cleanest code you will ever see....
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Goal which runs QUnit tests in PhantomJs (by convention)
 * 
 * @goal test
 * 
 * @phase test
 */
public class PhantomJsQunitRunnerMojo extends AbstractMojo {

	/**
	 * Directory of JS src files to be tested.
	 * 
	 * @parameter expression="${qunit.jssrc.directory}"
	 * @required
	 */
	private File jsSourceDirectory;

	/**
	 * Optional additional JavaScript files (comma-separated list)
	 * 
	 * @parameter expression="${qunit.jsinc.files}"
	 */
	private File[] jsSourceIncludes;

	/**
	 * Directory of JS test files.
	 * 
	 * @parameter expression="${qunit.jstest.directory}"
	 * @required
	 */
	private File jsTestDirectory;

	/**
	 * Base directory of project/
	 * 
	 * @parameter expression="${basedir}"
	 */
	private File baseDirectory;

	/**
	 * Directory containing the build files
	 * 
	 * @parameter expression="${project.build.directory}"
	 */
	private File buildDirectory;

	/**
	 * Boolean to fail build on failure
	 * 
	 * @parameter expression="${maven.test.failure.ignore}" default-value=false
	 */
	private boolean ignoreFailures;

	/**
	 * Optional path to PhantomJs executable
	 * 
	 * @parameter expression="${phantomjs.path}"
	 * @required
	 */
	private String pathToPhantomJs;

	/**
	 * Optional Xvfb DISPLAY number for PhantomJS
	 * 
	 * @parameter expression="${phantomjs.display}"
	 */
	private int phantomJsDisplay;
	
	/**
	 * JUnit-compliant XML reports directory 
	 * 
	 * @parameter expression="${project.reports.directory}"
	 * @required
	 */
	private String xmlOutputDirectory;

	/**
	 * Filenames of JS test files from the jsTestDirectory to exclude.
	 * 
	 * @parameter alias="excludes";
	 */
	private String[] mExcludes;
	// XXX Add excludes logic

	private static final String pathToResources = "src/main/resources";
	private static final String qUnitJsFileName = "qunit-git.js";
	private static final String phantomJsQunitRunner = "phantomjs-qunit-runner.js";
	private static final String jsTestFileSuffix = "Test.js";
	//private static final String jUnitXmlDirectoryName = "junitxml";
	
	public void setExcludes(String[] excludes) {
		mExcludes = excludes;
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		int retCode = 0;

		// Go over all the js test files in jsTestDirectory
		for (File temp : getJsTestFiles(jsTestDirectory.toString())) {
			// Run each through phantomJs to test
			retCode += runQUnitInPhantomJs(temp);
		}

		if (!ignoreFailures) {
			// If ever retCode is more than 1, it means error(s) have occurred
			if (retCode > 0) {
				throw new MojoFailureException("One or more QUnit tests failed");
			}
		}
	}
	
	protected List<File> getJsFiles(File directory, final String suffix) {
		List<File> files = new LinkedList<File>();
		
		// FilenameFilter that accept sub-directories and *<suffix> files
		FilenameFilter jsFileFilter = new FilenameFilter() {
			public boolean accept(File dir, String filename) {
				File f = new File(dir.toString() + "/" + filename);
				return f.isDirectory() || f.isFile() && (suffix == null || filename.endsWith(suffix));
			}
		};
		
		for(File f : directory.listFiles(jsFileFilter)) {
			if(f.isFile()) {
				files.add(f);
			}
			else {
				files.addAll( getJsFiles(f, suffix) );
			}
		}
		return files;
	}

	protected File[] getJsIncludeFiles() {
		return jsSourceIncludes;
	}

	private int runQUnitInPhantomJs(File testFile) {
		int exitVal = 255;
		try {

			String testFileName = testFile.toString().substring( jsTestDirectory.toString().length() + 1 );
			this.getLog().info("Running " + testFileName);
			
			// Set parameters
			// need to be
			//     1. phantomjs
			//     2. phantomjsqunitrunner.js
			//     3. qunit.js
			//     4. <Test Directory>
			//     5. <Test File Name> (relative to <Test Directory>)
			//     6. <Source File Name> (absolute)
			//     ... global dependencies
			//     ... test dependencies
			List<String> params = new LinkedList<String>();

			// Set path to PhantomJs
			params.add(pathToPhantomJs + "/" + "phantomjs");

			// Copy phantomJsQunitRunner and qUnitJsFileName over from
			// phantomjs-qunit-runner plugin over for use..
			try {
				FileUtils.copyInputStreamToFile(
						this.getClass().getClassLoader().getResourceAsStream(phantomJsQunitRunner),
						new File(buildDirectory + "/" + phantomJsQunitRunner)
					);
				FileUtils.copyInputStreamToFile(
						this.getClass().getClassLoader().getResourceAsStream(qUnitJsFileName),
						new File(buildDirectory + "/" + qUnitJsFileName)
					);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			
			// phantomjsqunitrunner.js && qunit.js

			// Set param 1 and 3 to the previously copied files
			params.add(buildDirectory + "/" + phantomJsQunitRunner);
			params.add(buildDirectory + "/" + qUnitJsFileName);
			
			// Test Directory
			params.add(jsTestDirectory.toString());
			
			// Test File Name
			params.add(testFileName);

			// Source File Name
			params.add(jsSourceDirectory + "/" + testFileName.substring(0, testFileName.indexOf(jsTestFileSuffix)) + ".js");

			// Add <jsSourceIncludes> to the parameters
			for(File temp : getJsIncludeFiles()) {
				params.add(temp.toString());
			}

			// Add <jsSourceIncludes> to the parameters
			for(File temp : getDependencies(testFileName)) {
				params.add(temp.toString());
			}

			// Add DISPLAY environment variable
			// This allow PhantomJS to be run in a fake-X environment (Xvfb)
			ProcessBuilder prb = new ProcessBuilder(params);
			prb.environment().put("DISPLAY", ":" + phantomJsDisplay + ".0");
			this.getLog().debug("DISPLAY = " + prb.environment().get("DISPLAY"));

			String parametersString = "";
			for(String param : params) {
				parametersString += param + " ";
			}
			this.getLog().debug(parametersString);

			Process pr = prb.start();

			// Grab STDOUT of execution (this is the junit xml output generated
			// by the js), write to file
			BufferedReader input = new BufferedReader(new InputStreamReader(
					pr.getInputStream()));
			File jUnitXmlOutputPath = new File(buildDirectory + "/"
					+ xmlOutputDirectory);

			jUnitXmlOutputPath.mkdir();
			BufferedWriter output = new BufferedWriter(new FileWriter(
					jUnitXmlOutputPath + "/" + testFile.getName() + ".xml"));

			String line = null;
			while ((line = input.readLine()) != null) {
				output.write(line + "\n");
			}

			output.close();
			exitVal = pr.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return exitVal;
	}

	protected File[] getDependencies(String testFileName) {
		List<File> dependencies = new LinkedList<File>();
		File temp = new File(jsTestDirectory + "/" + testFileName.replaceFirst("\\.js$", ".txt"));
		if(temp.exists()) {
			try {
				for(String filename : FileUtils.readLines(temp)) {
					dependencies.add(new File(filename));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else {
			this.getLog().debug("File " + jsTestDirectory + "/" + testFileName.replaceFirst("\\.js$", ".txt") + " does not exist");
		}
		return dependencies.toArray(new File[0]);
	}

	private File[] getJsTestFiles(String dirName) {
		File dir = new File(dirName);
		return getJsFiles(dir, jsTestFileSuffix).toArray(new File[0]);
	}
}
