package net.kennychua.phantomjs_qunit_runner;
/*
 * This is not the cleanest code you will ever see....
 */
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

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
	
	private List<File> _allFiles = new LinkedList<File>();

	private static final String qUnitJsFileName = "qunit-git.js";
	private static final String qUnitCssFileName = "qunit-git.css";
	private static final String jsTestFileSuffix = "Test.js";
	private static final String qUnitHtmlOutputDirectoryName = "qunit-html";
	
	private static final String qUnitHeader = "<!DOCTYPE html>\n"
			+ "<html>\n"
			+ "<head>\n"
			+ "<meta charset=\"utf-8\">\n"
			+ "<title>QUnit Test Suite</title>\n"
			+ "<link rel=\"stylesheet\" href=\"qunit-git.css\" type=\"text/css\" media=\"screen\">\n"
			+ "<script type=\"text/javascript\" src=\"qunit-git.js\"></script>\n";
	
	private static final String qUnitFooter = "</head>"
			+ "<body>"
			+ "<h1 id=\"qunit-header\">QUnit Test Suite</h1>\n"
			+ "<h2 id=\"qunit-banner\"></h2>\n"
			+ "<div id=\"qunit-testrunner-toolbar\"></div>\n"
			+ "<h2 id=\"qunit-userAgent\"></h2>\n"
			+ "<ol id=\"qunit-tests\"></ol>\n"
			+ "</body>\n"
			+ "</html>";
	
	private static String qUnitHtmlOutputPath;

	public void execute() throws MojoExecutionException, MojoFailureException {
		qUnitHtmlOutputPath = buildDirectory + "/" + qUnitHtmlOutputDirectoryName;

		// Go over all the js test files in jsTestDirectory
		File[] testFiles = getJsTestFiles(jsTestDirectory);
		for (File temp : testFiles) {
			
			String testFileName = temp.toString().substring( jsTestDirectory.toString().length() + 1 );
			this.getLog().info("Generating QUnit HTML page for " + testFileName);
			
			// Run each through phantomJs to test
			generateQunitHtmlOutput(testFileName);
		}
		
		// Generate all.html
		if(testFiles.length > 0) {
			generateQunitHtmlOutput("all.html");
		}
	}

	protected File[] getJsIncludeFiles() {
		return jsSourceIncludes;
	}
	
	protected String getJsTestPath(String testFileName) {
		return jsTestDirectory + "/" + testFileName.replaceFirst("\\.js$", "");
	}
	
	protected File[] getDependencies(String testFileName) {
		
		String path = getJsTestPath(testFileName);
		List<File> dependencies = new LinkedList<File>();
		
		// <filename>.txt dependencies 
		File tempFile = new File(path + ".txt");
		if(tempFile.exists()) {
			this.getLog().debug("Found file " + path + ".txt");
			try {
				for(String filename : FileUtils.readLines(tempFile)) {
					dependencies.add(new File(filename).getAbsoluteFile());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else {
			this.getLog().debug("File " + path + ".txt does not exist");
		}
		
		return dependencies.toArray(new File[0]);
	}
	
	// <filename>/* dependencies
	protected File[] getResources(String testFileName) {
		
		String path = getJsTestPath(testFileName);
		List<File> resources = new LinkedList<File>();
		
		File tempDir = new File(path);
		if(!tempDir.exists()) {
			this.getLog().debug("Resource directory " + path + " does not exist");
		}
		else if(!tempDir.isDirectory()) {
			this.getLog().error("Resource directory " + path + " is not a directory");
		}
		else {
			this.getLog().debug("Found resource directory " + path);
			for(String filename : tempDir.list()) {
				resources.add(new File(testFileName.replaceFirst("\\.js$", "") + "/" + filename));
			}
		}
		
		return resources.toArray(new File[0]);
	}

	private void generateQunitHtmlOutput(String testFileName) throws MojoExecutionException {
	
		// Create folder
		File qUnitHtmlOutputPathFile = new File(qUnitHtmlOutputPath);
		if(!qUnitHtmlOutputPathFile.exists()) {
			this.getLog().debug("   - Creating " + qUnitHtmlOutputPathFile);
			try {
				FileUtils.forceMkdir(qUnitHtmlOutputPathFile);
			} catch (IOException e) {
				throw new MojoExecutionException("Can't create directory " + qUnitHtmlOutputPathFile, e);
			}
		}
		
		// Create the QUnit HTML wrapper files
		this.getLog().debug("   - Writing " + testFileName.replaceAll(".*/([^/]+)", "$1").replaceAll("\\.js$", ".html"));
		writeQunitHtmlFile(testFileName);
		
		if(testFileName != "all.html") {
			
			// Copy the QUnit Js and Css files
			copyQunitResources();
			
			// Copy the Js source files to be tested
			copyJsFiles(testFileName);
		}
	}

	private void copyQunitResources() throws MojoExecutionException {
		// copy qunit js & css
		try {
			File cssFile = new File(qUnitHtmlOutputPath + "/" + qUnitCssFileName);
			File jsFile  = new File(qUnitHtmlOutputPath + "/" + qUnitJsFileName);
			if(!cssFile.exists()) {
				FileUtils.copyInputStreamToFile(this.getClass().getClassLoader().getResourceAsStream(qUnitCssFileName), cssFile);
			}
			if(!jsFile.exists()) {
				FileUtils.copyInputStreamToFile(this.getClass().getClassLoader().getResourceAsStream(qUnitJsFileName), jsFile);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Can't copy QUnit resource files", e);
		}
	}
	
	private void copyJsFiles(String testFileName) throws MojoExecutionException {
		
		// work out what the source js file name is
		// eg abcTest.js resolves to abc.js
		// then, copy BOTH files for qunit to run nicely in a browser
		
		// Copy from current plugin to the buildDir of the calling project.. 
		try {
			
			// Copy resources
			String resourceSourcePath      = testFileName.replaceFirst("\\.js", "");
			String resourceDestinationPath = new File(testFileName).getName().replaceFirst("\\.js", "");
			File   resourceSourceDir       = new File(jsTestDirectory + "/" + resourceSourcePath);
			File   resourceDestinationDir  = new File(qUnitHtmlOutputPath + "/" + resourceDestinationPath);
			if(!resourceSourceDir.exists()) {
				this.getLog().debug("Resource directory " + resourceSourceDir + " does not exist");
			}
			else if(!resourceSourceDir.isDirectory()) {
				this.getLog().error("Resource directory " + resourceSourceDir + " already exists and is not a directory");
			}
			else if(resourceDestinationDir.exists() && !resourceDestinationDir.isDirectory()) {
				this.getLog().error("Resource directory " + resourceDestinationDir + " already exists and is not a directory");
			}
			else {
				this.getLog().debug("Found resource directory " + resourceSourceDir);
				if(resourceDestinationDir.exists() && resourceDestinationDir.list().length > 0) {
					this.getLog().debug("   - Cleaning resource directory " + resourceDestinationDir);
					FileUtils.cleanDirectory(resourceDestinationDir);
				}
				else {
					this.getLog().debug("   - Resource directory " + resourceDestinationDir + (resourceDestinationDir.exists() ? " is already empty" : " does not exist, nothing to empty"));
				}
				this.getLog().debug("   - Copying resource directory " + resourceSourcePath + " to " + qUnitHtmlOutputPath.replaceAll(".*/([^/]+)", "$1") + "/" + resourceDestinationPath);
				FileUtils.copyDirectory(resourceSourceDir, resourceDestinationDir);
			}
		}
		catch (IOException e) {
			throw new MojoExecutionException("Can't copy Javascript files for " + testFileName, e);
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
				+ "\"></script>\n";
	}
	
	private String getRelativePath(File script, File outputPath) {
		File projectDirectory = new File("");
		String sourceRelativePath = outputPath.getAbsolutePath().replaceFirst(Pattern.quote(projectDirectory.getAbsolutePath().toString()), "").replaceAll(Pattern.quote(File.separator) + "[^" + Pattern.quote(File.separator) + "]+", "../");
		return sourceRelativePath + script.getAbsolutePath().replaceFirst(Pattern.quote(projectDirectory.getAbsolutePath() + File.separator), "").replaceAll(Pattern.quote(File.separator), "/");
	}

	private void writeQunitHtmlFile(String testFileName) throws MojoExecutionException {
		
		String jsTestFile = testFileName.replaceAll(".*" + Pattern.quote(File.separator) + "([^" + Pattern.quote(File.separator) + "]+)", "$1");
		jsTestFile = jsTestFile.replaceAll("\\.js", ".html");
		
		BufferedWriter output;
		try {
			output = new BufferedWriter(new FileWriter(qUnitHtmlOutputPath + File.separator + jsTestFile));
			output.write(qUnitHeader);
			
			File qUnitHtmlOutputDirectory = new File(qUnitHtmlOutputPath);
			
			// Common include files
			for(File temp : getJsIncludeFiles()) {
				output.write(generateScriptTag(getRelativePath(temp, qUnitHtmlOutputDirectory)));
			}
			
			// Specific include files
			if(testFileName == "all.html") {
				for(File temp : _allFiles) {
					output.write(generateScriptTag(getRelativePath(temp, qUnitHtmlOutputDirectory)));
				}
			}
			else {
				for(File temp : getDependencies(testFileName)) {
					output.write(generateScriptTag(getRelativePath(temp, qUnitHtmlOutputDirectory)));
					if(!_allFiles.contains(temp)) {
						_allFiles.add(temp);
					}
				}
				File sourceFile = new File(jsSourceDirectory + File.separator + testFileName.substring(0, testFileName.indexOf(jsTestFileSuffix)) + ".js");
				File testFile   = new File(jsTestDirectory + File.separator + testFileName);
				output.write(generateScriptTag(getRelativePath(sourceFile, qUnitHtmlOutputDirectory)));
				output.write(generateScriptTag(getRelativePath(testFile, qUnitHtmlOutputDirectory)));
				if(!_allFiles.contains(sourceFile)) {
					_allFiles.add(sourceFile);
				}
				if(!_allFiles.contains(testFile)) {
					_allFiles.add(testFile);
				}
			}
			output.write(qUnitFooter);
			output.close();

		} catch (IOException e) {
			throw new MojoExecutionException("Can't write QUnit HTML file " + testFileName, e);
		}
	}
}
