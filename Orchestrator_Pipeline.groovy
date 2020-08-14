def checkStatuses = "".toString();
def toAddress = "DL_59BA2AAB5F99B79157000011@exchange.sap.corp".toString()
//def toAddress = "boyan.tomov@sap.com".toString()
pipeline {
    options {
      timeout(time: 10, unit: 'MINUTES')
  }
    agent any
    environment {

        checkInAvsFailed = false
        checkInDirectFailed = false
		checkInOrchestratorMetrics = false
        checkInElkCertManFailed = false
		checkInElkRabitMQFailed = false
		checkInElkOrchDBHAFailed = false
		checkInElkOrchDBFailed = false
		checkInThousandEyes = false


		
        //python variables
        EVAL_DATA = '$ENV_EVAL_DATA'
        DIRECT_URL = '$ENV_DIRECT_URL'

        //Java for Variables General Deploy issues

        DC = '$WHERE'
		AUTH = credentials("puser")

        TARGET1 = 'ELK'  //Check in ELK
		TARGET2 = 'LOG'	//Check in LOG
        elkLogStage = "Check Logs for $TARGET"

        //Java Variables for Check 2 and 3 about profiling logs.
		CHECK_NAME1 = 'CERTMANCONFIGURATION'
        CHECK_NAME2 = 'RABITMQCLONNECTIONCLOSED'
        CHECK_NAME3 = 'ORCHDBFAILOVER'
		CHECK_NAME4 = 'ORCHDBISSUES'


    }

    stages {
        stage('CLEAN') {
            failFast true
            steps {
                cleanWs()
            }
        }
		
		
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

							echo "Looking for 500"
							checkInAvsFailed = true
							checkInDirectFailed = true
							checkStatuses = checkStatuses.concat("1. Direct status: DOWN\n")
							unstable("Direct check status is DOWN")

						} else {
							echo "Did not found any issues in AvS or Direct. All good"
							checkStatuses = checkStatuses.concat("1. Direct status: UP\n")
							checkInAvsFailed = false
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
		stage("Checking Orchestrator VMs metrics for CRITICAL") {

            steps {
                script {
                    try {
						CHECK_METRICS_1 = sh(script: "cat $ENV_JOBS/CockpitAPI/orchestrator.json", returnStdout: true).toString().trim()
						echo CHECK_METRICS_1
						if (CHECK_METRICS_1.toString().contains("CRITICAL") || CHECK_METRICS_1.toString().contains("Critical")) {
							checkInOrchestratorMetrics = true
							checkStatuses = checkStatuses.concat("2. Orchestrator metrics contain CRITICAL: BAD!\n")
							unstable("Orchestrator metrics contain CRITICAL")
						} else {

							checkStatuses = checkStatuses.concat("2. Orchestrator metrics does not contain CRITICAL OK!\n")

						}
                    }
                    catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }

        stage("Checking Cert Manager for configuration errors") {

            steps {
                script {
					try{
						CHECK_IN_ELK1 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET1 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME1", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK1
						if (CHECK_IN_ELK1.toString().contains("ERROR")) {
							checkInElkCertManFailed = true
							checkStatuses = checkStatuses.concat("3. Log scanning results for Cert Manager Issues: Errors found!\n")
							unstable("Errors were found. Check the logs for more.")
						} else {
							checkStatuses = checkStatuses.concat("3. Log scanning results for Cert Manager Issues: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}					
                }
            }
        }
		stage("Checking RABITMQ service for connection is already closed errors") {

            steps {
                script {
					try {
						CHECK_IN_ELK2 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET1 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME2", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK2
						if (CHECK_IN_ELK2.toString().contains("ERROR")) {
							checkInElkRabitMQFailed = true
							checkStatuses = checkStatuses.concat("4. Log scanning results for RABITMQ Issues: Errors found!\n")
							unstable("Errors were found. Check the logs for more.")
						} else {
							checkStatuses = checkStatuses.concat("4. Log scanning results for RABITMQ Issues: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}					
                }
            }
        }
			stage("Checking ORCH DB for failover which could lead to errors") {

            steps {
                script {
					try {
						CHECK_IN_ELK3 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET1 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME3", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK3

						if (CHECK_IN_ELK3.toString().contains("ERROR")) {
							checkInElkOrchDBHAFailed = true
							checkStatuses = checkStatuses.concat("5. Log scanning results for Orchestrator DB Fail-over Issues: Errors found!\n")
							unstable("Errors were found. Check the logs for more.")
						} else {
							checkStatuses = checkStatuses.concat("5. Log scanning results for Orchestrator DB Fail-over Issues: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}					
                }
            }
        }
		stage("Checking for problems with the orchestrator DB. ") {

            steps {
                script {
					try {
						CHECK_IN_ELK4 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET1 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME4", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK4
						if (CHECK_IN_ELK4.toString().contains("ERROR")) {
							checkInElkOrchDBFailed = true
							checkStatuses = checkStatuses.concat("6. Log scanning results for Orchestrator DB Issues: Errors found!\n")
							unstable("Errors were found. Check the logs for more.")
						} else {
							checkStatuses = checkStatuses.concat("6. Log scanning results for Orchestrator DB Issues: OK!\n")
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
                    ((checkInAvsFailed == true && checkInDirectFailed == true) && (checkInOrchestratorMetrics == true || checkInElkOrchDBHAFailed == true || checkInElkRabitMQFailed == true || checkInElkCertManFailed == true || checkInElkOrchDBFailed == true))
                    //true
                }
            }
            steps {
                script {
                    def bodymail = """


The pipeline consists of 6 stages:
1. Checking Reverse Proxy Monitor - UP/DOWN + direct requests if the AvS status is down.
2. Monitoring cockpit metrics check for Orchestrator service  -  Filtering for "CRITICAL" status for the Orchestrator VMs
3. Checking CERT MANAGER logs for Configuration Errors - this might affect the Reverse-proxy functionality
"Configuration error: Invalid create, modify, or delete for class_string_item" AND file_name: ljs_trace AND component: certmanagerservice"
TIP: There's a problem with the Load Balancer such as that from OUTAGE-4365
Check - https://wiki.wdf.sap.corp/wiki/pages/viewpage.action?pageId=1887997135
4. Checking RABITMQ service for 'connection is already closed' errors
"com.rabbitmq.client.AlreadyClosedException: connection is already closed due to connection error" AND file_name: ljs_trace
5. Checking ORCH DB for failover which could lead to errors
"ASE KNOWN HA ERROR FOUND" AND file_name: ljs_trace
TIP: This error indicates that there has been a switchover of the orchestrator HA ASE DB to the secondary node
6. Checking ORCH DB for general errors
"org.eclipse.persistence.exceptions.DatabaseException" AND component: orchestrator AND file_name: ljs_trace



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
                                subject: "IGOR: OUTAGE DETECTED ${WHERE} Orchestrator Dependencies/Reverse Proxy issues detected at:  ${formattedDate} UTC Build: $BUILD_NUMBER",
                                body: "Hi Colleagues,\n\nHere is an automated report from Igor pipeline that caught some issues in the Orchestrator Dependencies/Reverse Proxy: \n\n"+ checkStatuses + bodymail + "\n"  + "\nRegards, \nIgor"
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
