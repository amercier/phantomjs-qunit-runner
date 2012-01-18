// Credit to Rod of http://whileonefork.blogspot.com/2011/07/javascript-unit-tests-with-qunit-ant.html for inspiration of this js file
String.prototype.supplant = function(o) {
	return this.replace(/{([^{}]*)}/g, function(a, b) {
		var r = o[b];
		return typeof r === 'string' || typeof r === 'number' ? r : a;
	});
};

var JUnitXmlFormatter = {
	someProperty : 'some value here',
	printJUnitXmlOutputHeader : function(testsErrors, testsTotal, testsTotalRunTime, testsFailures, testsFileName) {
		console.log("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
		console.log("<testsuite errors=\"{_testsErrors}\" tests=\"{_testsTotal}\" time=\"{_testsTotalRunTime}\" failures=\"{_testsFailures}\" name=\"{_testsFileName}\">"
						.supplant({
							_testsErrors : testsErrors,
							_testsTotal : testsTotal,
							_testsTotalRunTime : testsTotalRunTime,
							_testsFailures : testsFailures,
							_testsFileName : testsFileName
						}));
	},
	printJUnitXmlTestCasePass : function(testName, testRunTime) {
		console.log("<testcase time=\"{_testRunTime}\" name=\"{_testName}\"/>"
				.supplant({
					_testRunTime : testRunTime,
					_testName : testName
				}));
	},
	printJUnitXmlTestCaseFail : function(testName, testRunTime, failureType,
			failureMessage) {
		console
				.log("<testcase time=\"{_testRunTime}\" name=\"{_testName}\">"
						.supplant({
							_testRunTime : testRunTime,
							_testName : testName
						}));
		console
				.log("<failure type=\"{_failureType}\" message=\"{_failureMessage}\">"
						.supplant({
							_failureType : failureType,
							_failureMessage : failureMessage
						}));
		console.log("PhantomJS QUnit failure on test : '{_testName}'"
				.supplant({
					_testName : testName
				}));
		console.log("</failure>");
		console.log("</testcase>");
	},
	printJUnitXmlOutputFooter : function() {
		console.log("</testsuite>");
	}
};

function importJs(scriptName) {
	if( !phantom.injectJs(scriptName) ) {
		console.error('File not found: ' + scriptName);
		phantom.exit(1);
	}
}

// Arg1 should be QUnit
importJs(phantom.args[0]);

// Arg 5+ should be included files
var usrIncScripts = [];
for(var i = 4 ; i < phantom.args.length ; i++) {
	usrIncScripts[i-3] = phantom.args[i];
	importJs(phantom.args[i]);
}

// Arg2 should be user tests directory
var usrTestDirectory = phantom.args[1];

// Arg3 should be user tests
var usrTestScript = phantom.args[2];
importJs(usrTestDirectory + '/' + usrTestScript);

// Arg4 should be user tests
var usrSrcScript = phantom.args[3];
importJs(usrSrcScript);

// Run QUnit
var testsPassed = 0;
var testsFailed = 0;
var testStartDate;
var testEndDate;
var testRunTime;
var totalRunTime = 0;

// extend copied from QUnit.js
function extend(a, b) {
	for ( var prop in b) {
		if (b[prop] === undefined) {
			delete a[prop];
		} else {
			a[prop] = b[prop];
		}
	}

	return a;
}
JUnitXmlFormatter.printJUnitXmlOutputHeader(0, testsPassed + testsFailed, totalRunTime, testsFailed, usrTestScript.replace(/\.js$/, '').replace(/\./g, '-').replace(/\//g, '.'));
QUnit.begin({});

// Initialize the config, saving the execution queue
var oldconfig = extend({}, QUnit.config);
QUnit.init();
extend(QUnit.config, oldconfig);

QUnit.testStart = function(t) {
	testStartDate = new Date();
}

QUnit.testDone = function(t) {
	testEndDate = new Date();
	testRunTime = testEndDate.getTime() - testStartDate.getTime();
	totalRunTime = parseInt(totalRunTime) + parseInt(testRunTime);

	if (0 === t.failed) {
		testsPassed++;
		JUnitXmlFormatter.printJUnitXmlTestCasePass(t.name, testRunTime);
	} else {
		testsFailed++;
		JUnitXmlFormatter.printJUnitXmlTestCaseFail(t.name, testRunTime, 1, 1);
	}
}

var running = true;
QUnit.done = function(i) {
	running = false;
}

// Instead of QUnit.start(); just directly exec; the timer stuff seems to
// invariably screw us up and we don't need it
QUnit.config.semaphore = 0;
while (QUnit.config.queue.length)
	QUnit.config.queue.shift()();

// wait for completion
var ct = 0;
while (running) {
	if (ct++ % 1000000 == 0) {
		// console.log('queue is at ' + QUnit.config.queue.length);
	}
	if (!QUnit.config.queue.length) {
		QUnit.done();
	}
}

JUnitXmlFormatter.printJUnitXmlOutputFooter();

// exit code is # of failed tests; this facilitates Ant failonerror.
// Alternately, 1 if testsFailed > 0.
phantom.exit(testsFailed);
