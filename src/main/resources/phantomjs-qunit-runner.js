// Credit to Rod of http://whileonefork.blogspot.com/2011/07/javascript-unit-tests-with-qunit-ant.html for inspiration of this js file
String.prototype.supplant = function(o) {
	return this.replace(/{([^{}]*)}/g, function(a, b) {
		var r = o[b];
		return (typeof r === 'string' || typeof r === 'number') ? r : a;
	});
};

if (!Function.prototype.bind) {
	Function.prototype.bind = function (oThis) {
		if (typeof this !== "function") {
			// closest thing possible to the ECMAScript 5 internal IsCallable function
			throw new TypeError("Function.prototype.bind - what is trying to be bound is not callable");
		}
		
		var aArgs = Array.prototype.slice.call(arguments, 1), 
		    fToBind = this, 
		    fNOP = function () {},
		    fBound = function () {
		    	return fToBind.apply(
		    			this instanceof fNOP && oThis
		    					? this
		    					: oThis,
		    			aArgs.concat(Array.prototype.slice.call(arguments))
		    		);
		};
		
		fNOP.prototype = this.prototype;
		fBound.prototype = new fNOP();
		
		return fBound;
	};
}

var originalConsole = window.console,
    newConsole = {
		standardOutput: [],
		errorOutput   : [],
		log  : function() { this.standardOutput.push(joinArguments(arguments, "\n")); },
		info : function() { this.standardOutput.push(joinArguments(arguments, "\n")); },
		warn : function() { this.errorOutput.push(joinArguments(arguments, "\n")); },
		error: function() { if(!/^Test .* died, exception and test follows$/.test(arguments[0])) this.errorOutput.push(joinArguments(arguments, "\n")); },
		reset: function() {
			this.standardOutput = [];
			this.errorOutput = [];
		}
	};

var JUnitXmlFormatter = {
	lines: [],
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
	addLine: function(line) {
		this.lines.push(line);
	},
	addJUnitXmlTestCasePass : function(testName, testRunTime, standardOutput, standardError) {
		this.addLine("\t<testcase time=\"{_testRunTime}\" name=\"{_testName}\">".supplant({
				_testRunTime : testRunTime,
				_testName : testName
			}));
		this.addJUnitXmlSystemOut(standardOutput);
		this.addJUnitXmlSystemErr(standardError);
		this.addLine("\t</testcase>");
	},
	addJUnitXmlTestCaseFail : function(testName, testRunTime, failureType, failureMessage, standardOutput, standardError) {
		this.addLine("\t<testcase time=\"{_testRunTime}\" name=\"{_testName}\">".supplant({
				_testRunTime : testRunTime,
				_testName : testName
			}));
		this.addLine("\t\t<failure type=\"{_failureType}\" message=\"{_failureMessage}\">Test {_testName} died, exception and test follows</failure>".supplant({
				_failureType : failureType,
				_failureMessage : failureMessage,
				_testName : testName
			}));
		this.addJUnitXmlSystemOut(standardOutput);
		this.addJUnitXmlSystemErr(standardError);
		this.addLine("\t</testcase>");
	},
	printJUnitXmlOutputBody : function() {
		console.log(this.lines.join("\n"));
	},
	printJUnitXmlOutputFooter : function() {
		console.log("</testsuite>");
	},
	addJUnitXmlSystemOut: function(standardOutput, global) {
		if(standardOutput.length) {
			this.addLine(((global ? "\t" : "\t\t") + "<system-out>\n{_standardOutput}\n" + (global ? "\t" : "\t\t") + "</system-out>").supplant({
					_standardOutput: standardOutput.join("\n").replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/&/g,'&amp;')
				}));
		}
	},
	addJUnitXmlSystemErr: function(standardError, global) {
		if(standardError.length) {
			this.addLine(((global ? "\t" : "\t\t") + "<system-err>\n{_standardError}\n" + (global ? "\t" : "\t\t") + "</system-err>").supplant({
					_standardError: standardError.join("\n").replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/&/g,'&amp;')
				}));
		}
	}
};

var globalSystemOut = [];
var globalSystemErr = [];
function importJs(scriptName) {
	console = newConsole;
	try {
		if( !phantom.injectJs(scriptName) ) {
			throw new Error('File not found: ' + scriptName);
		}
	}
	catch(e) {
		globalSystemErr.push('File ' + scriptName + ': ' + e);
	}
	for(var i = 0 ; i < newConsole.errorOutput.length ; i++) {
		globalSystemErr.push('File ' + scriptName + ': ' + newConsole.errorOutput[i]);
	}
	for(var i = 0 ; i < newConsole.standardOutput.length ; i++) {
		globalSystemOut.push('File ' + scriptName + ': ' + newConsole.standardOutput[i]);
	}
	newConsole.reset();
	console = originalConsole;
}

// Arg1 should be QUnit
importJs(phantom.args[0]);

// Run QUnit
var testsPassed = 0;
var testsFailed = 0;
var testStartDate;
var testEndDate;
var testRunTime;
var totalRunTime = 0;
var usrIncScripts;
var usrTestDirectory;
var usrTestScript;

// Arg 4+ should be included files
usrIncScripts = [];
for(var i = 4 ; i < phantom.args.length ; i++) {
	usrIncScripts[i-3] = phantom.args[i];
	importJs(phantom.args[i]);
}

//Arg3 should be user source file
if(phantom.args[3]) {
	importJs(phantom.args[3]);
}

// Arg2 should be user tests directory
usrTestDirectory = phantom.args[1];

// Arg3 should be user test
usrTestScript = phantom.args[2];
importJs(usrTestDirectory + '/' + usrTestScript);

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

function joinArguments(args, separator) {
	var result = '';
	for(var i = 0 ; i < args.length ; i++) {
		result += (i == 0 ? '' : separator) + args[i];
	}
	return result;
}

QUnit.begin({});

// Initialize the config, saving the execution queue
var oldconfig = extend({}, QUnit.config);
QUnit.init();
extend(QUnit.config, oldconfig);

QUnit.testStart = function(t) {
	
	testStartDate = new Date();
	newConsole.reset();
	console = newConsole; 
}

QUnit.testDone = function(t) {
	testEndDate = new Date();
	testRunTime = testEndDate.getTime() - testStartDate.getTime();
	totalRunTime = parseInt(totalRunTime) + parseInt(testRunTime);
	
	console = originalConsole;
	if (0 === t.failed) {
		testsPassed++;
		JUnitXmlFormatter.addJUnitXmlTestCasePass(t.name, testRunTime, newConsole.standardOutput, newConsole.errorOutput);
	} else {
		testsFailed++;
		JUnitXmlFormatter.addJUnitXmlTestCaseFail(t.name, testRunTime, 1, 1, newConsole.standardOutput, newConsole.errorOutput);
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

JUnitXmlFormatter.printJUnitXmlOutputHeader(0, testsPassed + testsFailed, totalRunTime, testsFailed, usrTestScript.replace(/\.js$/, '').replace(/\./g, '-').replace(/\//g, '.'));

if(globalSystemOut.length) {
	JUnitXmlFormatter.addJUnitXmlSystemOut(globalSystemOut, true);
}
if(globalSystemErr.length) {
	JUnitXmlFormatter.addJUnitXmlSystemErr(globalSystemErr, true);
}

JUnitXmlFormatter.printJUnitXmlOutputBody();
JUnitXmlFormatter.printJUnitXmlOutputFooter();

// exit code is # of failed tests; this facilitates Ant failonerror.
// Alternately, 1 if testsFailed > 0.
phantom.exit(testsFailed);
