package net.kennychua.phantomjs_qunit_runner;
/*
 * This is not the cleanest code you will ever see....
 */
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Goal which runs QUnit tests in PhantomJs (by convention)
 * 
 * @goal generate-html
 * 
 * @phase test
 */
public class QunitHtmlWrapperMojo extends AbstractMojo {
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
	 * Directory containing the build files
	 * 
	 * @parameter expression="${project.build.directory}"
	 */
	private File buildDirectory;

	private static final String qUnitJsFileName = "qunit-git.js";
	private static final String qUnitCssFileName = "qunit-git.css";
	private static final String jsTestFileSuffix = "Test.js";
	private static final String qUnitHtmlOutputDirectoryName = "qunit-html";
	private static final String qUnitHeader = "<html><head><title>QUnit Test Suite</title><link rel=\"stylesheet\" href=\"qunit-git.css\" type=\"text/css\" media=\"screen\"><script type=\"text/javascript\" src=\"qunit-git.js\"></script>";
	private static final String qUnitFooter = "</head><body><h1 id=\"qunit-header\">QUnit Test Suite</h1><h2 id=\"qunit-banner\"></h2><div id=\"qunit-testrunner-toolbar\"></div><h2 id=\"qunit-userAgent\"></h2><ol id=\"qunit-tests\"></ol></body></html>";
	private static String qUnitHtmlOutputPath;

	public void execute() throws MojoExecutionException, MojoFailureException {
		qUnitHtmlOutputPath = buildDirectory + "/" + qUnitHtmlOutputDirectoryName;

		// Copy include files
		try {
			for(File temp : getJsIncludeFiles()) {
				FileUtils.copyFile(temp, new File(qUnitHtmlOutputPath + "/" + temp.getName()));
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}

		// Go over all the js test files in jsTestDirectory
		for (File temp : getJsTestFiles(jsTestDirectory)) {
			
			String testFileName = temp.toString().substring( jsTestDirectory.toString().length() + 1 );
			this.getLog().info("Generating QUnit HTML page for " + testFileName);
			
			// Run each through phantomJs to test
			generateQunitHtmlOutput(testFileName);
		}
	}

	protected File[] getJsIncludeFiles() {
		return jsSourceIncludes;
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

	private void generateQunitHtmlOutput(String testFileName) {
	
		// Create folder
		this.getLog().debug("   - Creating " + qUnitHtmlOutputPath);
		new File(qUnitHtmlOutputPath).mkdir();
		
		// Create the QUnit HTML wrapper files
		this.getLog().debug("   - Writing " + testFileName.replaceAll(".*/([^/]+)", "$1"));
		writeQunitHtmlFile(testFileName);
		
		// Copy the QUnit Js and Css files
		copyQunitResources();
		
		// Copy the Js source files to be tested
		copyJsFiles(testFileName);
	}

	private void copyQunitResources() {
		// copy qunit js & css
		try {
			FileUtils.copyInputStreamToFile(this.getClass().getClassLoader().getResourceAsStream(qUnitCssFileName), new File(qUnitHtmlOutputPath + "/" + qUnitCssFileName));
			FileUtils.copyInputStreamToFile(this.getClass().getClassLoader().getResourceAsStream(qUnitJsFileName), new File(qUnitHtmlOutputPath + "/" + qUnitJsFileName));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void copyJsFiles(String testFileName) {
		
		// work out what the source js file name is
		// eg abcTest.js resolves to abc.js
		// then, copy BOTH files for qunit to run nicely in a browser
		String srcFileName = testFileName.substring(0, testFileName.indexOf(jsTestFileSuffix)) + ".js";
		
		// Copy from current plugin to the buildDir of the calling project.. 
		try {
			
			for(File dependency : getDependencies(testFileName)) {
				this.getLog().debug("   - Copying " + dependency + " to " + dependency.getName());
				FileUtils.copyFile(dependency, new File(qUnitHtmlOutputPath + "/" + dependency.getName()));
			}
			
			this.getLog().debug("   - Copying " + testFileName + " to " + testFileName.replaceAll(".*/([^/]+)", "$1"));
			FileUtils.copyFile(new File(jsTestDirectory + "/" + testFileName), new File(qUnitHtmlOutputPath + "/" + testFileName.replaceAll(".*/([^/]+)", "$1")));
			this.getLog().debug("   - Copying " + srcFileName + " to " + srcFileName.replaceAll(".*/([^/]+)", "$1"));
			FileUtils.copyFile(new File(jsSourceDirectory + "/" + srcFileName), new File(qUnitHtmlOutputPath + "/" + srcFileName.replaceAll(".*/([^/]+)", "$1")));
		} catch (IOException e) {
			e.printStackTrace();
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

	private File[] getJsTestFiles(File dir) {
		return getJsFiles(dir, jsTestFileSuffix).toArray(new File[0]);
	}

	private String generateScriptTag(String fileName) {
		return "<script type=\"text/javascript\" src=\"" + fileName
				+ "\"></script>";
	}

	private void writeQunitHtmlFile(String testFileName) {
		
		String jsTestFile = testFileName.replaceAll(".*/([^/]+)", "$1");
		String jsSrcFile = jsTestFile.substring(0, jsTestFile.indexOf(jsTestFileSuffix)) + ".js";
		BufferedWriter output;
		try {
			output = new BufferedWriter(new FileWriter(qUnitHtmlOutputPath + "/" + jsTestFile + ".html"));
			output.write(qUnitHeader);
			for(File temp : getJsIncludeFiles()) {
				output.write(generateScriptTag(temp.getName()));
			}
			for(File temp : getDependencies(testFileName)) {
				output.write(generateScriptTag(temp.getName()));
			}
			output.write(generateScriptTag(jsSrcFile));
			output.write(generateScriptTag(jsTestFile));
			output.write(qUnitFooter);
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
