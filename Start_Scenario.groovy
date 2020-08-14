def checkStatuses = "".toString();

def toAddress = "DL_59BA2AAB5F99B79157000011@exchange.sap.corp".toString()
//def toAddress = "hristo.popov@sap.com, boyan.tomov@sap.com".toString()

pipeline {
    agent any
    environment {
        checkInAvsFailed = false;
        checkInElkStartFailed = false
        checkInDirectFailed = false
        checkInElkLBFailed = false
        checkInElkZypperFailed = false
        checkInElkDBFailed = false
        checkInElkDomainDBFailed = false
		checkInElkChefFailed = false
		checkInElkThrotelingFailed = false
		checkInElkOrchToAccFailed = false
		checkInThousandEyes = false

        //python variables
        EVAL_DATA = '$ENV_EVAL_DATA'
        DIRECT_URL = '$ENV_DIRECT_URL'

        //Java for Variables General Deploy issues
        
        DC = '$WHERE'
        AUTH = credentials("puser")

        TARGET = 'ELK'  //could be LOG or ELK depending on where to search
        elkLogStage = "Check Logs for $TARGET"

        //Java Variables for Check 2 and 3 about profiling logs.
		CHECK_NAME1 = 'START_SCENARIO_PROBLEMS'
        CHECK_NAME2 = 'BIGIPCOREHC'
        CHECK_NAME3 = 'ORCHZYPPER'
        CHECK_NAME4 = 'ORCHDBISSUES'
        CHECK_NAME5 = 'ORCHTODOMAINDB'
		CHECK_NAME6 = 'CHEF_ERROR_RUBY_UNZIP'
		CHECK_NAME7 = 'THROTELING_ROLLING_UPDATE'
		CHECK_NAME8 = 'ORCHTOACCOUNTS'

    }

    stages {
        stage('Checking monitor in AvS and Directly') {
            steps {
                script {
					try {
						CHECK_IN_AVS = sh(script: "python $ENV_JOBS/avs_requests.py $EVAL_DATA $DIRECT_URL", returnStdout: true).toString().trim()
						echo CHECK_IN_AVS
						if (CHECK_IN_AVS.toString().contains("DOWN")) {
							checkInAvsFailed = true
							checkStatuses = checkStatuses.concat("1. AvS status: DOWN\n")
							unstable("Check status in AVS is DOWN")
						 }

						 else {

							checkStatuses = checkStatuses.concat("1. AvS status: UP\n")
						 }

						if (CHECK_IN_AVS.toString().contains("500")) {                        
							checkInDirectFailed = true
							checkStatuses = checkStatuses.concat("1. Direct status: DOWN\n")
							unstable("Direct check status is DOWN")

						} else {
							echo "Did not found any issues in AvS or Direct. All good"
							checkStatuses = checkStatuses.concat("1. Direct status: UP\n")
							checkInDirectFailed = false
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }

        stage('Network check - ThousandEyes alerts for checks 1,2,3 and 8') {
            when {
                expression {
                    (checkInAvsFailed == true && checkInDirectFailed == true)

                }
            }
            steps {
                script {
                    try {
                        CHECK_THOUSAND_EYES = (sh(script: "cat $ENV_JOBS/ThousandEyes/\"$WHERE\".json ", returnStdout: true).toString().trim())
                        echo CHECK_THOUSAND_EYES
                        if (CHECK_THOUSAND_EYES.toString().contains("active")) {
                            checkStatuses = checkStatuses.concat("3. ThousandEyes alert is active for this landscape: BAD \n")
                            unstable("There are active alerts for this landscape")
                            checkInThousandEyes = true


                        } else {

                            checkStatuses = checkStatuses.concat("3. No ThousandEyes alerts active for this landscape: OK \n")

                        }
                    }
                    catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }

        }


        stage('Mail Alert: OUTAGE - Network issues') {
            when {
                expression {
                    (checkInAvsFailed == true && checkInDirectFailed == true && checkInThousandEyes == true) 

                }
            }
            steps {
                script {
                    def bodymail = """

This email is sent as there are tree conditions met:
1. AvS has showed status DOWN
2. Direct check to $DIRECT_URL is down or $DIRECT_URL2 is down.
3. ThousandEyes has indicated that there are problems with checks 1,2,3 OR 8.

Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
Check build number: $BUILD_NUMBER



        """
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")


                    mail to: toAddress,
                            from: 'igor@mail.sap.hana.ondemand.com',
                            subject: "IGOR: NETWORK OUTAGE DETECTED ${WHERE} Connectivity Service issues detected at:  ${formattedDate} UTC",
                            body: "Hi Colleagues,\nHere is an automated report from Igor pipeline that caught some issues in the Network and Connectivity Service is affected: \n\n" + checkStatuses + bodymail + "\n" + "\nRegards, \nIgor"
                    echo "Mail Sent to " + toAddress


                }

            }

        }
        stage("Checking logs for past start operations") {

            when {
                expression {
                    checkInAvsFailed == true
                }
            }
            steps {
                echo "$elkLogStage"
                script {
					try {
						CHECK_IN_ELK = sh(script: "cd $ENV_JOBS && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME1", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK
						if (CHECK_IN_ELK.toString().contains("ERROR")) {
							checkInElkStartFailed = true
							checkStatuses = checkStatuses.concat("2. Log scanning results for past app start failures: Errors found!\n")
							unstable("Error scanning for start scenario failures showed that there are errors.Check the logs.")
						} else {
							checkStatuses.concat("Pattern matching for log scanning showed no results.\n")
							checkStatuses = checkStatuses.concat("2. Log scanning results for past app start failures: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }


        stage("Checking for failed LB operations") {

            when {
                expression {
                    checkInAvsFailed == true
                }
            }
            steps {
                echo "$elkLogStage"
                script {
					try {
						CHECK_IN_ELK2 = sh(script: "cd $ENV_JOBS && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME2", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK2

						if (CHECK_IN_ELK2.toString().contains("BigIpOperationSuccess=false")) {
							checkInElkLBFailed = true
							checkStatuses = checkStatuses.concat("3. Log scanning results for failed LB operations: BAD!\n")
							unstable("Error scanning for LB operation failures showed that there are errors when registering members or creating new pool.Check the logs.")
						} else {
							checkStatuses = checkStatuses.concat("3. Log scanning results for failed LB operations: OK!\n")

						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }


        stage("Checking logs for Zypper issues") {

            when {
                expression {
                    checkInAvsFailed == true
                }
            }
            steps {
                echo "$elkLogStage"
                script {
					try {
						CHECK_IN_ELK3 = sh(script: "cd $ENV_JOBS && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME3", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK3

						if (CHECK_IN_ELK3.toString().contains("ERROR")) {
							checkInElkZypperFailed = true
							checkStatuses = checkStatuses.concat("4. Log scanning results for Zypper/Repo issues: Errors found!\n")
							unstable("Error scanning for Zypper/Repo issues showed that there are errors.Check the logs.")
						} else {
							checkStatuses = checkStatuses.concat("4. Log scanning results for Zypper/Repo issues: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }


        stage("Checking logs for Orchestrator DB issues") {

            when {
                expression {
                    checkInAvsFailed == true
                }
            }
            steps {
                echo "$elkLogStage"
                script {
					try {
						CHECK_IN_ELK4 = sh(script: "cd $ENV_JOBS && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME4", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK4

						if (CHECK_IN_ELK4.toString().contains("ERROR")) {
							checkInElkZypperFailed = true
							checkStatuses = checkStatuses.concat("5. Log scanning results for Orchestrator DB issues: Errors found!\n")
							unstable("Error scanning for Orchestrator DB issues showed that there are errors.Check the logs.")
						} else {
							checkStatuses = checkStatuses.concat("5. Log scanning results for Orchestrator DB issues: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }
		

        stage("Checking logs for Orch to DomainDB issues") {

            when {
                expression {
                    checkInAvsFailed == true
                }
            }
            steps {
                echo "$elkLogStage"
                script {
					try {
						CHECK_IN_ELK5 = sh(script: "cd $ENV_JOBS && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME5", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK5

						if (CHECK_IN_ELK5.toString().contains("ERROR")) {
							checkInElkZypperFailed = true
							checkStatuses = checkStatuses.concat("6. Log scanning results for DomainDB communication(network or domainDB itself): Errors found!\n")
							unstable("Error scanning for DomainDB communication (network or domainDB itself) issues showed that there are errors.Check the logs.")
						} else {
							checkStatuses = checkStatuses.concat("6. Log scanning results for DomainDB communication(network or domainDB itself): OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }
		
		
		stage("Checking logs for CHEF ERROR in RUBY/UNZIP") {

            when {
                expression {
                    checkInAvsFailed == true
                }
            }
            steps {
                echo "$elkLogStage"
                script {
					try {
						CHECK_IN_ELK6 = sh(script: "cd $ENV_JOBS && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME6", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK6

						if (CHECK_IN_ELK6.toString().contains("ERROR")) {
							checkInElkChefFailed = true
							checkStatuses = checkStatuses.concat("7. Log scanning results for CHEF cookbook: errors found\n")
							unstable("The provisioning has failed on extracting a file. Possible problems: storage and network failure or slowness. Check the http_access log of the repo for the name of the file and see for how long it has been downloaded. Then try to download the file manually eithe via the httpstunnel or directly via the LB (http://repo-XY1.spa3.od.sap.biz:50000/repository/).Check the logs.")
						} else {
							checkStatuses = checkStatuses.concat("7. Log scanning results for CHEF cookbook: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }
		
		
		stage("Checking logs for THROTLING ISSUES (To many deploy/stop/start (rolling) operations") {

            when {
                expression {
                    checkInAvsFailed == true
                }
            }
            steps {
                echo "$elkLogStage"
                script {
					try {
						CHECK_IN_ELK7 = sh(script: "cd $ENV_JOBS && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME7", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK7

						if (CHECK_IN_ELK7.toString().contains("ERROR")) {
							checkInElkThrotelingFailed = true
							checkStatuses = checkStatuses.concat("8. The throttling limits are: REACHED. \n")
							unstable("The throttling limits are reached. To many deploy/stop/start - operations are expected to fail. This could be because of CPI update or DOS attack. Check the logs.")
						} else {
							checkStatuses = checkStatuses.concat("8. The throttling limits are: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }
		
		
		stage("Checking for access problems from Orchestrator to accounts.sap.com") {

            when {
                expression {
                    checkInAvsFailed == true
                }
            }
            steps {
                echo "$elkLogStage"
                script {
					try {
						CHECK_IN_ELK8 = sh(script: "cd $ENV_JOBS && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME8", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK8
						if (CHECK_IN_ELK8.toString().contains("ERROR")) {
							checkInElkOrchToAccFailed = true
							checkStatuses = checkStatuses.concat("9. Connection problems from Orchestrator to accounts.sap.com: FOUND! \n")
							unstable("Problems with accessing accounts.sap.com. This would cause problems with the users authentication. If this alert is received for all landscapes, then there is a serious, global problem with accounts.sap.com. If this alert is onl for one landscape, then check the connectivity to accounts.sap.com by using telnet to port 443 from an orchestrator on the landscape. Open a networking SPC ticket if the problem continues. Check the logs.")
						} else {
							checkStatuses = checkStatuses.concat("9. Connection problems from Orchestrator to accounts.sap.com: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }
		
		
		stage("Checking for failed stages") {  
            steps { 
                script {
                    CHECK_FAILURES = sh(script: "curl  ${BUILD_URL}/consoleText", returnStdout: true).toString().trim()
                    echo CHECK_FAILURES
                     if (CHECK_FAILURES.toString().contains("The stage Failed"))  {
                        
                        
                                 def bodymail = """

Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
"""
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")
                        mail to: toAddress,
                                from: 'igor@mail.sap.hana.ondemand.com',
                                subject: "IGOR: Pipeline is failing: ${JOB_NAME}  ${formattedDate} UTC Build: $BUILD_NUMBER",
                                body: "The Pipeline has failed. Please check all the UNSTABLE/Yellow stages to see why:"  + bodymail + "\n"  + "\nRegards, \nIgor"
                        echo "Mail Sent to " + toAddress
             
                    } else {
                        echo "No Failing stages found"
                    }
				}
			}  
		}
		
		
        stage('Send Mail Alert') {
            when {
                expression {
                    //true
                    checkInAvsFailed == true && (checkInDirectFailed == true || checkInElkLBFailed == true || checkInElkZypperFailed == true  || checkInElkDBFailed == true  || checkInElkDomainDBFailed == true || checkInElkOrchToAccFailed == true || checkInElkThrotelingFailed == true || checkInElkChefFailed == true)
                }
            }
            steps {
                script {
                    def bodymail = """


The pipeline consists of 10 stages:

1. AvS check for Start scenario - UP/DOWN + direct requests if the AvS status is down.

2. Checking for 'teststartscenario' VMs stop/start operations
"VM taken from pool" AND teststartscenario* AND component: orchestrator AND file_name: ljs_trace,
				updateVMStatus AND component: orchestrator AND file_name: ljs_trace,
				solo.sh AND component: orchestrator AND file_name: ljs_trace

3. Checking the Orchestrator to see if the LB operations failed when creating/deleting pool or adding member.
"com.sap.core.admin.server.lbupdater.BigIPUpdater" AND "BigIpOperationSuccess=false"240 AND teststart* AND component: orchestrator

4. Checking for Zypper/ Repo issues.
"Timeout exceeded when accessing" AND component: orchestrator AND file_name: ljs_trace

5. Checking for issues/errors with the Orchestrator DB.
"org.eclipse.persistence.exceptions.DatabaseException" AND component: orchestrator AND file_name: ljs_trace

6. Checking for communication issues with DomainDB. It might be caused by network or DomainDB issues.
"Unexpected response" AND component: orchestrator AND file_name: ljs_trace"

7. Checking for CHEF ERROR in RUBY/UNZIP
"unzip: cannot find zipfile" AND ruby_block AND file_name: ljs_trace

8. Checking for THROTLING ISSUES (To many deploy/stop/start/rolling operations)
"Reached maximum size: " AND file_name: ljs_trace AND component: orchestrator

9. Checking for access problems from Orchestrator to accounts.sap.com
"accounts.sap.com" AND "connect timed out" AND component: orchestrator AND file_name: ljs_trace

10. Send email report containing all the data.
NOTE: All checks => are executed, if we get failiure in check '1'


Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
Check build number: $BUILD_NUMBER
"""
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")



                        mail to: toAddress,
                                from: 'igor@mail.sap.hana.ondemand.com',
                                subject: "IGOR: ${WHERE} Orchestrator Start Scenario issues detected at:  ${formattedDate} UTC Build: $BUILD_NUMBER",
                                body: "Hi Colleagues,\n\nHere is an automated report from Igor pipeline that caught some issues in the Orchestrator Start Scenario: \n\n"+ checkStatuses + bodymail + "\n"  + "\nRegards, \nIgor"
                        echo "Mail Sent to " + toAddress



                }

            }

        }
                	stage("Creating artifacts") {

            steps {
                script {
                                        // write out some data about the job
                    def jobData = [job_url: "${BUILD_URL}"]
                    def jobDataText = groovy.json.JsonOutput.toJson(jobData)
                    writeFile file: "Hristo.json", text: "${checkStatuses}", encoding: 'UTF-8'

                    // archive the artifacts
                    archiveArtifacts artifacts: "Hristo.json", onlyIfSuccessful: false

                    echo "Artifacts done"

                }
            }
        }
    }
}
