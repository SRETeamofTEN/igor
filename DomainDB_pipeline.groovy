def checkStatuses = "".toString();
def toAddress = "hristo.popov@sap.com".toString()
def toAddressFailover = "hristo.popov@sap.com".toString()

//Email has to be redone after adding thousand eyes and Dynatrace integrations in 2 more stages

pipeline {
    options {
        timeout(time: 10, unit: 'MINUTES')
    }
    agent any
    environment {
        checkInAvsFailed = false;
        checkInAvsFailedAgain = false;
        checkInDirectFailed = false
        checkInDirectFailedAgain = false
        errorWasPresented = false
        
        checkInThousandEyes = false
        checkInElkConnsToDomainDB = false
        checkInElkDomainDBFailover = false
        checkInDomainDBMetrics = false
        checkInElkBorrowedConnectionsReached = false
        checkInElkUnableToReadUM = false
        checkInElkUnexpectedResponse = false
        checkInElkDomainDBSlow = false
        checkInElkSendToAuditLog = false
        checkInElkWriteToAudit = false
        checkInDynatrace = false


        //python variables
        EVAL_DATA = '$ENV_EVAL_DATA'
        DIRECT_URL = '$ENV_DIRECT_URL'

        //Java for Variables General Deploy issues

        DC = '$WHERE'

        AUTH = credentials("puser")
        TARGET = 'ELK'  //could be LOG or ELK depending on where to search
        TARGET2 = 'LOG'  //could be LOG or ELK depending on where to search
        elkLogStage = "Check Logs for $TARGET"

        //Java Variables for Check 2 and 3 about profiling logs.
        CHECK_NAME1 = 'CONNSTODOMAINDB'
        CHECK_NAME2 = 'DOMAINDB_FAILOVER_OCCURED'
        CHECK_NAME3 = 'DOMAINDB_POOLED_CONNECTIONS'
        CHECK_NAME4 = 'ORCHDOMAINDB2'
        CHECK_NAME5 = 'ORCHTODOMAINDB'
        CHECK_NAME6 = 'DEPLOY_PERFORMANCE'
        CHECK_NAME7 = 'DOMAINDB_AUDITLOG'
        CHECK_NAME8 = 'DOMAINDB_AUDITLOG2'

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
                    CHECK_IN_AVS = sh(script: "python $ENV_JOBS/avs_requests4.py $EVAL_DATA $DIRECT_URL $AUTH_USR $AUTH_PSW", returnStdout: true).toString().trim()
                    echo CHECK_IN_AVS

                    if ((CHECK_IN_AVS.toString() =~ /AVS STATUS: DOWN/) && (CHECK_IN_AVS.toString() =~ /Direct check Response code: 5[0-9]+/)) {
						checkInAvsFailed = true
						checkInDirectFailed = true
                        echo "Both Avs and direct indicated issues.."	
                        checkStatuses = checkStatuses.concat("1.1 Both AvS and Direct checks have indicated issues: BAD")
                        unstable("AVS and Direct check status are DOWN")
                        }
                    else if (CHECK_IN_AVS.toString() =~ /Direct check Response code: 5[0-9]+/) {
						checkInDirectFailed = true
                        echo "Direct check is down."
                        checkStatuses = checkStatuses.concat("1.1 AvS is fine, but Direct check is down. It might be flapping: BAD")
                        unstable("AVS is UP and Direct check status is DOWN")
                        }
                    else if (CHECK_IN_AVS.toString() =~ /"overall":"DOWN"|"recent":"DOWN"/) {
						checkInDirectFailed = true
                        echo "Direct check is down as response has overall:DOWN."
                        checkStatuses = checkStatuses.concat("1.1 Direct check is down as response has overall:DOWN:  BAD")
                        unstable("AVS is UP and Direct check status is DOWN")
                        }
                    else if (CHECK_IN_AVS.toString() =~ /AVS STATUS: DOWN/){
						checkInAvsFailed = true
                        echo "AvS check is down."
                        checkStatuses = checkStatuses.concat("1.1 AvS check is down, but Direct is working. It is a false positive: OK")
                        unstable("AVS is DOWN and Direct check status is UP")
                        }
                    else if (CHECK_IN_AVS.toString() =~ /(ERROR|Traceback)/) {
                        errorWasPresented = true 
                        unstable("There was an error in the python script. Forsing second attempt. ")
                    }
                    else{
                        echo " All seems fine." 
                        checkStatuses = checkStatuses.concat("<b>1.1 AvS and Direct are fine: OK</b> \n")
                    }
                    } catch (err) {
                        errorWasPresented = true 
                        echo err.getMessage()
                        unstable("There was an error in the stage. Set the variable to test it in the next stage. ")
                    }

                }
            }
        }

        stage('Double-checking as it failed in previous stage.') {
		        when {
                expression {
                    ((checkInAvsFailed == true && checkInDirectFailed == true) || errorWasPresented == true)
                }
            }
            steps {
                script {
                    sleep(30)
                      try {
                    CHECK_IN_AVS_AGAIN = sh(script: "python $ENV_JOBS/avs_requests3.py $EVAL_DATA $DIRECT_URL $AUTH_USR $AUTH_PSW", returnStdout: true).toString().trim()
                    echo CHECK_IN_AVS_AGAIN

                    if ((CHECK_IN_AVS_AGAIN.toString() =~ /AVS STATUS: DOWN/) && (CHECK_IN_AVS_AGAIN.toString() =~ /Direct check Response code: 5[0-9]+/)) {
						checkInAvsFailedAgain = true
						checkInDirectFailedAgain = true
                        echo "Both Avs and direct indicated issues.."	
                        checkStatuses = checkStatuses.concat("1.2 Both AvS and Direct checks have indicated issues: BAD")
                        unstable("AVS and Direct check status are DOWN")
                        }
                    else if (CHECK_IN_AVS_AGAIN.toString() =~ /Direct check Response code: 5[0-9]+/) {
						checkInDirectFailedAgain = true
                        echo "Direct check is down."
                        checkStatuses = checkStatuses.concat("1.2 AvS is fine, but Direct check is down. It might be flapping: BAD")
                        unstable("AVS is UP and Direct check status is DOWN")
                        }
                    else if (CHECK_IN_AVS_AGAIN.toString() =~ /"overall":"DOWN"|"recent":"DOWN"/) {
						checkInDirectFailedAgain = true
                        echo "Direct check is down as response has overall:DOWN."
                        checkStatuses = checkStatuses.concat("1.2 Direct check is down as response has overall:DOWN:  BAD")
                        unstable("AVS is UP and Direct check status is DOWN")
                        }
                    else if (CHECK_IN_AVS_AGAIN.toString() =~ /AVS STATUS: DOWN/){
						checkInAvsFailedAgain = true
                        echo "AvS check is down."
                        checkStatuses = checkStatuses.concat("1.2 AvS check is down, but Direct is working. It is a false positive: OK")
                        unstable("AVS is DOWN and Direct check status is UP")
                        }
                    else if (CHECK_IN_AVS_AGAIN.toString() =~ /(ERROR|Traceback)/) {
                        errorWasPresentedAgain = true 
                        echo "Second attempt has finished with error in the python script. Setting it to FAILURE"
                        currentBuild.result = 'FAILURE'
                    }
                    else{
                        echo " All seems fine." 
                        checkStatuses = checkStatuses.concat("1.2 AvS and Direct are fine: OK")
                    }
                    } catch (err) {
                        errorWasPresented = true 
                        echo err.getMessage()
                        echo "Second attempt has finished with error in the python script. Setting it to FAILURE"
                        currentBuild.result = 'FAILURE'
                    }

                }
            }
        }

        stage('Checking if there are ThousandEyes alerts for checks 1,2,3 and 8 for') {
            steps {
                script {
                    CHECK_THOUSAND_EYES = (sh(script: "cat $ENV_JOBS/ThousandEyes/\"$WHERE\".json ", returnStdout: true).toString().trim())
                    echo CHECK_THOUSAND_EYES
                    if (CHECK_THOUSAND_EYES.toString().contains("active")) {
                        checkStatuses = checkStatuses.concat("2. ThousandEyes alert is active for this landscape: BAD \n")
                        unstable("There are active alerts for this landscape")
                        checkInThousandEyes = true
                    } else {
                        checkStatuses = checkStatuses.concat("2. No ThousandEyes alerts active for this landscape: OK \n")
                    }
                }
            }
        }
		
		
        stage('Mail Alert: OUTAGE - Network issues') {
            when {
                expression {
                    checkInAvsFailedAgain == true && checkInDirectFailedAgain == true && checkInThousandEyes == true
                }
            }
            steps {
                script {
                    def bodymail = """

This email is sent as there are tree conditions met:
1. AvS has showed status DOWN
2. Direct check to $DIRECT_URL is down.
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
                            from: SENDER,
                            subject: "IGOR: OUTAGE DETECTED (NETWORK)  ${WHERE} $JOB_NAME ${formattedDate} UTC",
                            body: "Hi Colleagues,\n\nHere is an automated report from Igor pipeline that caught some issues in DomainDB: \n\n" + checkStatuses + bodymail + "\n" + "\nRegards, \nIgor"
                    echo "Mail Sent to " + toAddress
                }
            }
        }
		
		
        stage("Checking if there was a DomainDB failover.") {
            steps {
                echo "$elkLogStage"
                script {
                    CHECK_IN_ELK2 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET2 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME2", returnStdout: true).toString().trim()
                    echo CHECK_IN_ELK2					
					if (CHECK_IN_ELK2.toString().contains("Checking $LANDSCAPE")) {
	                    if (CHECK_IN_ELK2.toString().contains("Exception")) {
	                        checkInElkDomainDBFailover = true
	                        checkStatuses = checkStatuses.concat("3. Log scanning results DomainDB failover: BAD!\n")
	                        unstable("Logs indicated that there was a failover.Check the logs.")
	                        //sh(script: "echo \"There was a failover at ${LANDSCAPE} ${checkStatuses}  ${CHECK_IN_ELK2} \" | mail -s \"Failover alert at ${LANDSCAPE} \" -- matthias.gradl@sap.com, valentin.zipf@sap.com")
	                    } else {
	                        checkStatuses = checkStatuses.concat("3. Log scanning results DomainDB failover: OK!\n")
	                    }
					} else {
						checkInElkDomainDBFailover = true
						checkStatuses = checkStatuses.concat("3. Log scanning results DomainDB failover: ERROR while trying to reach LogSearch!\n")
						unstable("ERROR while trying to reach LogSearch!")
					}
                }
            }
        }

		
        stage("Checking DomainDB metrics for CRITICAL") {
            steps {
                script {
                    CHECK_METRICS_1 = sh(script: "cat $ENV_JOBS/CockpitAPI/domaindb.json", returnStdout: true).toString().trim()
                    echo CHECK_METRICS_1
                    if (CHECK_METRICS_1.toString().contains("CRITICAL") || CHECK_METRICS_1.toString().contains("Critical")) {
                        checkInDomainDBMetrics = true
                        checkStatuses = checkStatuses.concat("4. DomainDB metrics contain CRITICAL: BAD!\n")
                        unstable("DomainDB metrics contain CRITICAL")
                    } else {
                        checkStatuses = checkStatuses.concat("4. DomainDB metrics does not contain CRITICAL OK!\n")
                    }
                }
            }
        }

		
        stage("Checking for events with DomainDB in Dynatrace") {
			when {
				expression {
					("$ENV_DYNATRACE_EXISTS".toBoolean() == true )
				}
			}
            steps {
                script {
                    CHECK_DYNATRACE = sh(script: "cd $ENV_JOBS/CockpitAPI && jq '.problems[] | select(.tagsOfAffectedEntities[].value==\"domaindb\")' dynatrace.json", returnStdout: true).toString().trim()
                    echo CHECK_DYNATRACE
                    if (CHECK_DYNATRACE.toString().contains("OPEN")) {
                        checkInDynatrace = true
                        checkStatuses = checkStatuses.concat("5. We found some events in Dynatrace: BAD!\n")
                        unstable("There are some events in Dynatrace, master!")
                    } else {
                        checkStatuses = checkStatuses.concat("5. No events in Dynatrace: OK!\n")
                    }
                }
            }
        }
		
		
        stage("Checking connectivity service to DomainDB for errors") {
            when {
                expression {
                    (("$ENV_COMPLETE_TESTRUN".toBoolean() == true ) || (checkInAvsFailed == true))
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    CHECK_IN_ELK = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET2 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME1", returnStdout: true).toString().trim()
                    echo CHECK_IN_ELK
					if (CHECK_IN_ELK.toString().contains("Checking $LANDSCAPE")) {										
	                    if (CHECK_IN_ELK.toString().contains("Could not retrieve tenant form DomainDB")) {
	                        checkInElkConnsToDomainDB = true
	                        checkStatuses = checkStatuses.concat("6. Log scanning results for Connectivity service to DomainDB: Errors found!\n")
	                        unstable("Errors were found. Check the logs for more.")
	                    } else {
	                        checkStatuses = checkStatuses.concat("6. Log scanning results for Connectivity service to DomainDB: OK!\n")
	                    }
					} else {
						checkInElkConnsToDomainDB = true
						checkStatuses = checkStatuses.concat("6. Log scanning results for Connectivity service to DomainDB: ERROR while trying to reach LogSearch!\n")
						unstable("ERROR while trying to reach LogSearch!")
					}
                }
            }
        }


        stage("Checking logs for max borrowed connections") {
            when {
                expression {
                    (("$ENV_COMPLETE_TESTRUN".toBoolean() == true ) || (checkInAvsFailed == true))
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    CHECK_IN_ELK3 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET2 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME3", returnStdout: true).toString().trim()
                    echo CHECK_IN_ELK3
					if (CHECK_IN_ELK3.toString().contains("Checking $LANDSCAPE")) {
	                    if (CHECK_IN_ELK3.toString().contains("pooled")) {
	                        checkInElkBorrowedConnectionsReached = true
	                        checkStatuses = checkStatuses.concat("7. Log scanning for max borrowed connections: Errors found!\n")
	                        unstable("Logs indicated that all the connections were borrowed.Check the logs.")
	                    } else {
	                        checkStatuses = checkStatuses.concat("7. Log scanning for max borrowed connections: OK!\n")
	                    }
					} else {
						checkInElkBorrowedConnectionsReached = true
						checkStatuses = checkStatuses.concat("7. Log scanning for max borrowed connections: ERROR while trying to reach LogSearch!\n")
						unstable("ERROR while trying to reach LogSearch!")
					}
                }
            }
        }

		
        stage("Orchestrator unable to read UM configuration from DomainDB") {
            when {
                expression {
                    (("$ENV_COMPLETE_TESTRUN".toBoolean() == true ) || (checkInAvsFailed == true))
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    CHECK_IN_ELK4 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME4", returnStdout: true).toString().trim()
                    echo CHECK_IN_ELK4
					if (CHECK_IN_ELK4.toString().contains("Checking $LANDSCAPE")) {	
	                    if (CHECK_IN_ELK4.toString().contains("ERROR")) {
	                        checkInElkUnableToReadUM = true
	                        checkStatuses = checkStatuses.concat("8. Log scanning results for Orchestrator reading UM configuration from DomainDB: Errors found!\n")
	                        unstable("Error when Orchestrator tries to fetch UM configuration from DomainDB.Check the logs.")
	                    } else {
	                        checkStatuses = checkStatuses.concat("8. Log scanning results for Orchestrator reading UM configuration from DomainDB: OK!\n")
	                    }
					} else {
						checkInElkUnableToReadUM = true
						checkStatuses = checkStatuses.concat("8. Log scanning results for Orchestrator reading UM configuration from DomainDB: ERROR while trying to reach LogSearch!\n")
						unstable("ERROR while trying to reach LogSearch!")
					}
                }
            }
        }

		
        stage("Checking Orchestrator logs for faulty/unexpected response from DomainDB") {
            when {
                expression {
                    (("$ENV_COMPLETE_TESTRUN".toBoolean() == true ) || (checkInAvsFailed == true))
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    CHECK_IN_ELK5 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME5", returnStdout: true).toString().trim()
                    echo CHECK_IN_ELK5
					if (CHECK_IN_ELK5.toString().contains("Checking $LANDSCAPE")) {
	                    if (CHECK_IN_ELK5.toString().contains("ERROR")) {
	                        checkInElkUnexpectedResponse = true
	                        checkStatuses = checkStatuses.concat("9. Log scanning results unexpected domainDB response: Errors found!\n")
	                        unstable("DomainDB has returned 500 to orchestrator.Check the logs.")
	                    } else {
	                        checkStatuses = checkStatuses.concat("9. Log scanning results for unexpected domainDB response: OK!\n")
	                    }
					} else {
						checkInElkUnexpectedResponse = true
						checkStatuses = checkStatuses.concat("9. Log scanning results unexpected domainDB response: ERROR while trying to reach LogSearch!\n")
						unstable("ERROR while trying to reach LogSearch!")
					}
                }
            }
        }
		
		
        stage("Checking Profiling logs for domainDB slowness") {
            when {
                expression {
                    (("$ENV_COMPLETE_TESTRUN".toBoolean() == true ) || (checkInAvsFailed == true))
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    CHECK_IN_ELK6 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME6", returnStdout: true).toString().trim()
                    echo CHECK_IN_ELK6
					if (CHECK_IN_ELK6.toString().contains("Checking $LANDSCAPE")) {	
	                    def checkForDomainDBSlownesResults = (CHECK_IN_ELK6 =~ /system db] finished for \[([0-9]+)sec./)
	
	                    for (i = 0; i < checkForDomainDBSlownesResults.count; i++) {
	                        if (checkForDomainDBSlownesResults[i][1].toInteger() > 40) {
	                            println checkForDomainDBSlownesResults[i][1]
	                            checkStatuses = checkStatuses.concat("10. Log scanning results for DomainDB slowness: BAD!\n")
	                            unstable("DomainDB is running slow. Check the Logs for more output on when it said system db] finished for .... ")
	                            checkInElkDomainDBSlow = true
	                        }
	                    }
	                    if (CHECK_IN_ELK6.toString().contains("ERROR")) {
	                        checkInElkDomainDBSlow = true
	                        checkStatuses = checkStatuses.concat("10. Log scanning results for DomainDB slowness: BAD!\n")
	                        unstable("Error scanning for deploy scenario failures showed that there are errors.Check the logs.")
	                    } else {
	                        checkStatuses.concat("10. Pattern matching for log scanning showed no results.\n")
	                        echo "Logs not checked or there are no results"
	                    }
					} else {
						checkInElkDomainDBSlow = true
						checkStatuses = checkStatuses.concat("10. Log scanning results for DomainDB slowness: ERROR while trying to reach LogSearch!\n")
						unstable("ERROR while trying to reach LogSearch!")
					}
                }
            }
        }
		
		
        stage("DomainDB is unable to send messages to Audit Log") {
            when {
                expression {
                    (("$ENV_COMPLETE_TESTRUN".toBoolean() == true ) || (checkInAvsFailed == true))
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    CHECK_IN_ELK7 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET2 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME7", returnStdout: true).toString().trim()
                    echo CHECK_IN_ELK7
					if (CHECK_IN_ELK7.toString().contains("Checking $LANDSCAPE")) {
	                    if (CHECK_IN_ELK7.toString().contains("ERROR")) {
	                        checkInElkSendToAuditLog = true
	                        checkStatuses = checkStatuses.concat("11. DomainDB is unable to send messages to Audit Log: BAD \n")
	                        unstable("DomainDB is unable to send messages to AuditLog due to AuditLog failure or network issue. Check the logs.")
	                    } else {
	                        checkStatuses = checkStatuses.concat("11. DomainDB is unable to send messages to Audit Log: OK!\n")
	                    }
					} else {
						checkInElkSendToAuditLog = true
						checkStatuses = checkStatuses.concat("11. DomainDB is unable to send messages to Audit Log: ERROR while trying to reach LogSearch! \n")
						unstable("ERROR while trying to reach LogSearch!")
					}
                }
            }
        }

		
        stage("DomainDB is unable to write to to Audit Log server") {
            when {
                expression {
                    (("$ENV_COMPLETE_TESTRUN".toBoolean() == true ) || (checkInAvsFailed == true))
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    CHECK_IN_ELK8 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET2 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME8", returnStdout: true).toString().trim()
                    echo CHECK_IN_ELK8
					if (CHECK_IN_ELK8.toString().contains("Checking $LANDSCAPE")) {
	                    if (CHECK_IN_ELK8.toString().contains("ERROR")) {
	                        checkInElkWriteToAudit = true
	                        checkStatuses = checkStatuses.concat("12. DomainDB is unable to write to Audit Log: BAD \n")
	                        unstable("DomainDB is not able to write to audit log server. Check the logs.")
	                    } else {
	                        checkStatuses = checkStatuses.concat("12. DomainDB is able to write to Audit Log: OK!\n")
	                    }
					} else {
						checkInElkWriteToAudit = true
						checkStatuses = checkStatuses.concat("12. DomainDB is unable to write to Audit Log: ERROR while trying to reach LogSearch! \n")
						unstable("ERROR while trying to reach LogSearch!")
						
					}
                }
            }
        }
		
		
        stage("Mail Alert: OUTAGE Service is down or there is a dependency issue") {

            when {
                expression {
                    (checkInAvsFailedAgain == true && checkInDirectFailedAgain == true) || "$ENV_COMPLETE_TESTRUN".toBoolean() == true
                }
            }
            steps {
                script {
                    def bodymail = """


Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
Check build number: $BUILD_NUMBER

"""
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")
                    mail to: toAddress,
                            from: SENDER,
                            subject: "IGOR: OUTAGE DETECTED (Service Or Dependency) ${WHERE} DOMAINDB issues detected at:  ${formattedDate} UTC",
                            body: "Hi Colleagues,\n\nThere was a failover at $WHERE landscape. Please check the report:  \n\n" + checkStatuses + bodymail + "\n" + "\nRegards, \nIgor"
                    echo "Mail Sent to " + toAddressFailover
                }
            }
        }
		
		
        stage("Send mail to DomainDB colleagues for failover") {

            when {
                expression {
                    (checkInElkDomainDBFailover == true)
                }
            }
            steps {
                script {
                    def bodymail = """


Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
Check build number: $BUILD_NUMBER

"""
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")
                    mail to: toAddress,
                            from: SENDER,
                            subject: "IGOR: OUTAGE DETECTED ${WHERE} DOMAINDB issues detected at:  ${formattedDate} UTC",
                            body: "Hi Colleagues,\n\nThere was a failover at $WHERE landscape. Please check the report:  \n\n"  + checkStatuses + bodymail +"\nRegards, \nIgor"
                    echo "Mail Sent to " + toAddressFailover
					        checkInAvsFailed = false;
							

                }
            }
        }

}

}
