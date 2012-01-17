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
	private static final String jUnitXmlDirectoryName = "junitxml";

	public void setExcludes(String[] excludes) {
		mExcludes = excludes;
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		int retCode = 0;

		// Go over all the js test files in jsTestDirectory
		for (File temp : getJsTestFiles(jsTestDirectory.toString())) {
			// Run each through phantomJs to test
			retCode += runQUnitInPhantomJs(temp.getName().toString(),
					jsTestDirectory.toString());
		}

		if (!ignoreFailures) {
			// If ever retCode is more than 1, it means error(s) have occurred
			if (retCode > 0) {
				throw new MojoFailureException("One or more QUnit tests failed");
			}
		}
	}

	private int runQUnitInPhantomJs(String testFile, String testFileDirectory) {
		int exitVal = 255;
		try {
			// Set paramaters
			// needs to be : phantomjs phantomjsqunitrunner qunit.js AbcTest.js
			// Abc.js
			// Abc.js
			String[] params = new String[5];
			// XXX todo : unix executable. how to store and pull down from
			// nexus?

			// Set path to PhantomJs
			params[0] = pathToPhantomJs + "/" + "phantomjs";

			// Copy phantomJsQunitRunner and qUnitJsFileName over from
			// phantomjs-qunit-runner plugin over for use..
			try {
				FileUtils.copyInputStreamToFile(
						this.getClass().getClassLoader()
								.getResourceAsStream(phantomJsQunitRunner),
						new File(buildDirectory + "/" + phantomJsQunitRunner));
				FileUtils.copyInputStreamToFile(this.getClass()
						.getClassLoader().getResourceAsStream(qUnitJsFileName),
						new File(buildDirectory + "/" + qUnitJsFileName));
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Set param 1 and 3 to the previously copied files
			params[1] = buildDirectory + "/" + phantomJsQunitRunner;
			params[2] = buildDirectory + "/" + qUnitJsFileName;
			// params[1] =
			// this.getClass().getClassLoader().getResource(phantomJsQunitRunner).toString();
			// params[2] =
			// this.getClass().getClassLoader().getResource(qUnitJsFileName).toString();
			params[3] = testFileDirectory + "/" + testFile;
			// Some dirty string manipulation here to resolve js src file
			params[4] = jsSourceDirectory + "/"
					+ testFile.substring(0, testFile.indexOf(jsTestFileSuffix))
					+ ".js";

			Process pr = new ProcessBuilder(params).start();

			// Grab STDOUT of execution (this is the junit xml output generated
			// by the js), write to file
			BufferedReader input = new BufferedReader(new InputStreamReader(
					pr.getInputStream()));
			File jUnitXmlOutputPath = new File(buildDirectory + "/"
					+ jUnitXmlDirectoryName);

			jUnitXmlOutputPath.mkdir();
			BufferedWriter output = new BufferedWriter(new FileWriter(
					jUnitXmlOutputPath + "/" + testFile + ".xml"));

			String line = null;
			while ((line = input.readLine()) != null) {
				output.write(line);
			}

			output.close();
			exitVal = pr.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return exitVal;
	}

	private File[] getJsTestFiles(String dirName) {
		File dir = new File(dirName);

		return dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String filename) {
				return filename.endsWith(jsTestFileSuffix);
			}
		});
	}
}
