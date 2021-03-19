import groovy.json.JsonSlurperClassic
import java.time.LocalDateTime
final LocalDateTime currentTime = LocalDateTime.now()
def access_token = "".toString()
def checkStatusesSlack = "".toString();
def toAddressFailover = "DL_593FCDF97BCF84DAE500046C@exchange.sap.corp, DL_59BA2AAB5F99B79157000011@exchange.sap.corp".toString()

pipeline {
	options {
		timeout(time: 10, unit: 'MINUTES')
		timestamps()
	}
	
	agent any
	environment {
		AUTH = credentials("puser")
		checkInAvsFailed = false;
        checkInDirectFailed = false
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
        checkDBMetrics = false
        
		shouldSendResolvedAlert = false
        shouldSendAlert = false

        //python variables
        EVAL_DATA = '$ENV_EVAL_DATA'
        DIRECT_URL = '$ENV_DIRECT_URL'

        //Java for Variables General Deploy issues

        DC = '$WHERE'


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

    stage ("Run first batch of parallel stages"){
    parallel {	
		stage("AvS Check") {
		    
			steps {
				script {
				   
					//echo "Access token from he next stage: $access_token"
					// read all the content of the file into a single string
                    File fh1 = new File('/usr/sap/ljs/home/.jenkins/jobs/token.txt')
                    token = fh1.getText('UTF-8')
					//File('/path/to/file').getText('UTF-8')
					def bearer = "Bearer " + token
					//echo "Bearer is: $bearer"
					def avs_url = "https://avs-backend.cfapps.us10.hana.ondemand.com/api/v2/evaluationdata/${ENV_EVAL_DATA}/status"
					def avs_mon_url = "https://availability.cfapps.us10.hana.ondemand.com/index.html#/evaluation/${ENV_EVAL_DATA}"
					def status = 1
                    try{
					for (int i = 0; i < 5; i++) {
						def response = httpRequest acceptType: 'APPLICATION_JSON', customHeaders: [[maskValue: false, name: 'Authorization', value: "${bearer}"]], responseHandle: 'LEAVE_OPEN', url: avs_url , timeout: 30, wrapAsMultipart: false, validResponseCodes: '100:599'
						
						println("Content: "+response.content)
						def json = response.content
						def jsonObj = readJSON text: json
						evalname = "${jsonObj.evaluationName}"
						statusValue = "${jsonObj.status_value}"
						outageReason = "${jsonObj.outage_reason}"
						echo "$evalname	 \n$statusValue  \n$outageReason".toString()
						
						status = response.status
						if (status == 200) {
							//echo "access_token is: ${jsonObj.access_token}"

							println ("$statusValue")
							if (statusValue == "[DOWN]") {
							    checkStatusesSlack = checkStatusesSlack.concat(":warning: [EXTERNAL] The Status Value reported of the monitor <$avs_mon_url | $evalname> in AvS: *$statusValue* \n")
							    checkInAvsFailed = true
							    unstable("The Status Value reported of the monitor in AvS is: $statusValue")
							    response.close()
							    break
							    }
							response.close()
							checkInAvsFailed = false
							checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: [EXTERNAL] The Status Value reported of the monitor <$avs_mon_url | $evalname > in AvS: *$statusValue* \n")
							break
						} else {
							sleep i
						}
				
				    }
				}
				catch (err) {
					checkInAvsFailed = true
                    echo err.getMessage()
    			    unstable("The request towards AvS has failed and response is: $status")
                }
				    if (status != 200 || status == null || status == "") {
				        checkInAvsFailed = true
					    checkStatusesSlack = checkStatusesSlack.concat(":warning: [EXTERNAL] AvS status could not be obtained <$avs_mon_url | URL>. Response from AvS: *$status* \n")
					    unstable("The request towards AvS has failed and response is: $status")
				}
			}
		}
    }
		stage('Direct check to destination URL') {

			steps {
				script {
					def status = 0
					def recent_check = ""
					def overall_check = ""
					def sum = 0 
					def retry = 3 
					def directCheck = ""
					try {
						for (int i = 0; i < retry; i++) {
							def response = httpRequest acceptType: 'APPLICATION_JSON', responseHandle: 'LEAVE_OPEN', url: "$ENV_DIRECT_URL ", timeout: 30, wrapAsMultipart: false, validResponseCodes: '100:599'
							//println("Content: "+response.content)
							status = response.status
							directCheck = directCheck.concat(" $status")
							if (status == 200) {
								def json = response.content
								println ("$response.content")
								def jsonObj = readJSON text: json
								//echo "access_token is: ${jsonObj.access_token}"
	                            recent_check = recent_check.concat(" ${jsonObj.domaindb.recent} ")
								overall_check = overall_check.concat(" ${jsonObj.overall} ")
								response.close()
								sum = sum + status
								sleep(2)
						} else {
							sleep i
						}
                        				
					}
					} catch (err) {
								echo err.getMessage()
								checkInDirectFailed = true
								unstable("The request towards $ENV_DIRECT_URL has failed and response is: $status")
							}
				    // Adding a check to sum all responses and devide to retry attempts. If not 200 or if the content has /DOWN|Null|null/ (down or there was no json object returned to be parsed), then the direct status should be down.
				    status = sum/retry 
				    if(status != 200 || (recent_check.toString() =~ (/DOWN|Null|null/)) || (overall_check.toString() =~  (/DOWN|Null|null/))) {
				        checkInDirectFailed = true
					    checkStatusesSlack = checkStatusesSlack.concat(":warning: [INTERNAL] Direct status <$ENV_DIRECT_URL | URL> *$directCheck, Recent Check: $recent_check, Overall Check $overall_check: ERROR!* \n")
					    unstable("Direct status Response codes: $directCheck, Recent Check from JSON: $recent_check, Overall Check from JSON: $overall_check")
					    
					    
					}
					
					else{
					    checkInDirectFailed = false
					    checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: [INTERNAL] Direct status <$ENV_DIRECT_URL | URL> *$directCheck, Recent Check: $recent_check, Overall Check: $overall_check : OK* \n")
					
					}
			    }
		    }
        }
        stage('Checking if there are ThousandEyes alerts for checks 1,2,3 and 8') {
            steps {
                script {
                    
                    println ("Description:\n Test#1: External E2E Check to Portal Page in this DC \n Test#2: External Check to L7 Loadbalancers \n Test#3: External Check to L3 Loadbalancers \n Test#8: Outbound calls to Internet")
                    CHECK_THOUSAND_EYES = (sh(script: "cat $ENV_JOBS/ThousandEyes/\"$WHERE\".json ", returnStdout: true).toString().trim())
                    echo CHECK_THOUSAND_EYES
                    if (CHECK_THOUSAND_EYES.toString().contains("active")) {
                        checkStatusesSlack = checkStatusesSlack.concat(":warning: [INTERNAL/EXTERNAL] ThousandEyes active alerts: *ERROR!* \n")
                        unstable("There are active alerts for this landscape")
                        checkInThousandEyes = true
                    } else {
                        checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: [INTERNAL/EXTERNAL] No ThousandEyes active alerts: *OK* \n")
                    }
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
	                    if (CHECK_IN_ELK2.toString().contains("failover")) {
	                        checkInElkDomainDBFailover = true
	                        checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Domain DB failover: *ERROR!* \n")
	                        unstable("Logs indicated that there was a failover.Check the logs.")
	                        
	                    } else {
	                        checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: [LS] NO Domain DB failover: *OK* \n")
	                    }
					} else {
						
						checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Domain DB failover: *ERROR while trying to reach LogSearch!* \n")
						unstable("ERROR while trying to reach LogSearch!")
					}
                }
            }
        }
    }
}
        stage("Alert: OUTAGE - Network issues detected ") {
            when {
                expression {
                    (checkInAvsFailed == true && checkInThousandEyes == true)
                }
            }
            steps {
                script {
                    def serviceStatus = readFile '/usr/sap/ljs/home/.jenkins/userContent/tmp/DomainDB.status'
                    if (serviceStatus.contains("1")){
                        echo "Alert was sent from previous execution. Not sending one now"
                    }
                    else{
                    writeFile file: '/usr/sap/ljs/home/.jenkins/userContent/tmp/DomainDB.status', text: '1'
                    def DATETIME_TAG = date.format("MMMM dd HH:mm")
                    def bodySlack = """

Hi Colleagues,

There was *NETWORK* issue detected with *DOMAIN DB*:

1. AvS has showed status: *DOWN*
2. *ThousandEyes* has indicated that there are problems with checks *1,2,3,8 or 12.*

$checkStatusesSlack

Job <$JOB_URL | URL>
Job <$JOB_URL$BUILD_NUMBER/console | Console Output>
Job <https://wiki.wdf.sap.corp/wiki/pages/createpage.action?spaceKey=JPaaS&fromPageId=2419142177 | legend>

Regards,
Igor
"""
                    def subjectTextSlack = """IGOR:*[$WHERE]* OUTAGE DETECTED *(NETWORK)* with *DOMAINDB* service at: $DATETIME_TAG UTC""".toString()
                    slackSend color: '#C01025', message: subjectTextSlack + bodySlack                                
                    }                    


                }
            }
        }
		



stage ("Run second parallel batch"){
    parallel {
        stage("Checking DomainDB metrics for CRITICAL") {
            steps {
                script {
                    CHECK_METRICS_1 = sh(script: "sudo jq '.staticServices[] | select(.status==\"Critical\") | .host, .metrics[].output' $ENV_JOBS/CockpitAPI/domaindb.json | grep -v OK | grep 'vsa\\|CRITICAL' || echo \"Metrics are OK\"", returnStdout: true).toString().trim()
                    echo CHECK_METRICS_1
                    if (CHECK_METRICS_1.toString().contains("CRITICAL") || CHECK_METRICS_1.toString().contains("Critical")) {
                        checkInDomainDBMetrics = true
                        checkStatusesSlack = checkStatusesSlack.concat(":warning: Domain DB metrics: *ERROR!!*\n")
                        unstable("DomainDB metrics contain CRITICAL")
                    } else {
                        checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: Domain DB metrics: *OK!*\n")
						
                    
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
                        checkStatusesSlack = checkStatusesSlack.concat(":warning: Events in Dynatrace: *ERROR!!*\n")
                        unstable("There are some events in Dynatrace, master!")
                    } else {
                        checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: Events in Dynatrace: *OK!*\n")
                    }
                }
            }
        }
        stage("DomainDB databases state and metrics") {
            steps {
                script {
                    //variables 
                    def filePath = "/usr/sap/ljs/home/.jenkins/jobs/CockpitAPI/domaindb_database.json"
                    def file = new File(filePath)
                    def checkFile = file.canRead()
                        //Check if you can read the file. 
                        if (checkFile == true) {
                            CHECK_IN_ELK9 = readFile "$filePath"
                            echo CHECK_IN_ELK9
                            try{
                                //parse json and get status and metrics 
                                def jsonObj = readJSON text: CHECK_IN_ELK9
                                evalstatus = "${jsonObj.staticServices.status}"
                                evalhost = "${jsonObj.staticServices.host}"
                                // if status is Critical for some DB print that there are errors.  
            					if (evalstatus.toString().contains("Critical")) {
            					    println("Host: $evalhost \n Status: $evalstatus")
            					    println obj.getClass()
            					    checkStatusesSlack = checkStatusesSlack.concat(":warning: [METRICS] Database metrics: *ERROR!* \n")
            					    checkDBMetrics = true
            					    unstable("Critical metrics found")
            					} else {
            					    println("Host: $evalhost \n Status: $evalstatus")
            					    checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: [METRICS] Database metrics: *OK!*\n")
            					    checkDBMetrics = false
            					}
                            }catch (Exception e) {
                            println("Exception: ${e}")
                            unstable("File $file is not valid, missing or it is not really json file that can be parsed. ")
                              
                                }
                        } else {
                            //continue even if file does not persist. 
                            unstable("File $filePath either does not persist or it is not opening.")
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
	                    if (CHECK_IN_ELK.toString().contains("Could not retrieve tenant form Domain DB")) {
	                        checkInElkConnsToDomainDB = true
	                        checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Connectivity service to Domain DB: *ERROR!* \n")
	                        unstable("Errors were found. Check the logs for more.")
	                    } else {
	                        checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: [LS] Connectivity service to Domain DB: *OK!*\n")
	                    }
					} else {
						checkInElkConnsToDomainDB = true
						checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Connectivity service to Domain DB: *ERROR while trying to reach LogSearch!*\n")
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
	                        checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Max Borrowed connections: *ERROR!*\n")
	                        unstable("Logs indicated that all the connections were borrowed.Check the logs.")
	                    } else {
	                        checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: [LS] Max Borrowed connections: *OK!*\n")
	                    }
					} else {
						checkInElkBorrowedConnectionsReached = true
						checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Max Borrowed connections: *ERROR while trying to reach LogSearch!*\n")
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
	                        checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Orchestrator to Domain DB (UM CONF): *ERROR!* \n")
	                        unstable("Error when Orchestrator tries to fetch UM configuration from Domain DB.Check the logs.")
	                    } else {
	                        checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: [LS] Orchestrator to Domain DB (UM CONF): *OK!* \n")
	                    }
					} else {
						checkInElkUnableToReadUM = true
						checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Orchestrator to Domain DB (UM CONF): *ERROR while trying to reach LogSearch!* \n")
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
	                        checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Domain DB response to Orchestrator: *ERROR!*\n")
	                        unstable("DomainDB has returned 500 to orchestrator.Check the logs.")
	                    } else {
	                        checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: [LS] Domain DB response to Orchestrator: *OK!*\n")
	                    }
					} else {
						checkInElkUnexpectedResponse = true
						checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Domain DB response to Orchestrator: *ERROR while trying to reach LogSearch!*\n")
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
	                            checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Domain DB slowness: *ERROR!!*\n")
	                            unstable("DomainDB is running slow. Check the Logs for more output on when it said system db] finished for .... ")
	                            checkInElkDomainDBSlow = true
	                        }
	                    }
	                    if (CHECK_IN_ELK6.toString().contains("ERROR")) {
	                        checkInElkDomainDBSlow = true
	                        checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Domain DB slowness: *ERROR!!*\n")
	                        unstable("Error scanning for deploy scenario failures showed that there are errors.Check the logs.")
	                    } else {
	                        checkStatusesSlack.concat(":heavy_check_mark: [LS] Domain DB showed *no results/errors!*.\n")
	                        echo "Logs not checked or there are no results"
	                    }
					} else {
						checkInElkDomainDBSlow = true
						checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Domain DB slowness: *ERROR while trying to reach LogSearch!*\n")
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
	                        checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Domain DB send messages to Audit Log: *ERROR!* \n")
	                        unstable("DomainDB is unable to send messages to AuditLog due to AuditLog failure or network issue. Check the logs.")
	                    } else {
	                        checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: [LS] Domain DB send messages to Audit Log: *OK!*\n")
	                    }
					} else {
						checkInElkSendToAuditLog = true
						checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Domain DB send messages to Audit Log: *ERROR while trying to reach LogSearch!* \n")
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
	                        checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Domain DB writing to Audit Log: *ERROR!* \n")
	                        unstable("DomainDB is not able to write to audit log server. Check the logs.")
	                    } else {
	                        checkStatusesSlack = checkStatusesSlack.concat(":heavy_check_mark: [LS] Domain DB writing to Audit Log: *OK!*\n")
	                    }
					} else {
						checkInElkWriteToAudit = true
						checkStatusesSlack = checkStatusesSlack.concat(":warning: [LS] Domain DB writing to Audit Log: *ERROR while trying to reach LogSearch!* \n")
						unstable("ERROR while trying to reach LogSearch!")
					}
                }
            }
        }
		
    }
}		
		
		
        stage("Send mail to DomainDB colleagues for failover") {

            when {
                expression {
                    (checkInElkDomainDBFailover == true && checkInAvsFailed == true)
                }
            }
            steps {
                script {
                	def date = new Date()
                    def DATETIME_TAG = date.format("MMMM dd HH:mm")
                    def bodymail = """
Hi Colleagues,

There was a *FAILOVER* detected at of *DOMAIN DB*: 

Job <$JOB_URL | URL>
Job <$JOB_URL$BUILD_NUMBER/console | Console Output>
Job <https://wiki.wdf.sap.corp/wiki/display/JPaaS/DOMAIN+DB+SERVICE | legend>

Regards,
Igor
"""                 //leaving the email option due to domainDB colleagues
            def subjectText = "IGOR: *[$WHERE]* *DOMAINDB failover* detected at: $DATETIME_TAG UTC"
            def serviceStatus = readFile '/usr/sap/ljs/home/.jenkins/userContent/tmp/DomainDB.status'
            if (serviceStatus.toString().contains("1")){
            
                echo "Alert was sent from previous execution. Not sending one now"
            }
            else{
                //slackSend color: '#C01025', message: subjectText + bodymail
                //writeFile file: '/usr/sap/ljs/home/.jenkins/userContent/tmp/DomainDB.status', text: '1'
                mail to: toAddressFailover,
                from: SENDER,
                subject: subjectText,
                body: bodymail
                echo "Mail Sent to " + toAddressFailover
                
            }

                }
            }
        }

        stage("Report Analysis"){
            steps{
                script{
                    def serviceStatus = readFile '/usr/sap/ljs/home/.jenkins/userContent/tmp/DomainDB.status'
                    if (checkInAvsFailed == true && (checkInDirectFailed == true || checkInDynatrace == true || checkInDomainDBMetrics == true || checkInElkConnsToDomainDB == true || checkInElkBorrowedConnectionsReached == true || checkInElkUnableToReadUM == true || checkInElkUnexpectedResponse == true || checkInElkDomainDBSlow == true || checkInElkSendToAuditLog == true || checkInElkWriteToAudit == true || checkInElkDomainDBFailover == true)) {

                            if (serviceStatus.contains("1")){
                                echo "Alert was sent from previous execution. Not sending one now"
                            }
                            else{
                                shouldSendAlert = true
                                writeFile file: '/usr/sap/ljs/home/.jenkins/userContent/tmp/DomainDB.status', text: '1'
                                
                            }                        
                        
                    }
                    else if(checkInAvsFailed == true && checkInThousandEyes == true){
                        if (serviceStatus.contains("1")){
                            echo "Alert was sent from previous execution. Not sending one now"
                        }
                    }
                    else{
                        if (serviceStatus.contains("0")){
                                echo "Alert was sent from previous execution. Not sending one now"
                            }
                            else{
                                shouldSendResolvedAlert = true
                                writeFile file: '/usr/sap/ljs/home/.jenkins/userContent/tmp/DomainDB.status', text: '0'
                                
                            }         
                        
                    }
                
   
                }
            }
        }
        stage('Send Alert') {
            when {
                expression {
                    shouldSendAlert == true    
                    //true
                }
            }
            steps {
                
                script {
                    


//Slack body below 
def date = new Date()
def DATETIME_TAG = date.format("MMMM dd HH:mm")
def subjectTextSlack = """IGOR: *[$WHERE]* *DOMAINDB* (Service/Dependency) *OUTAGE* detected at: $DATETIME_TAG UTC""".toString()
def bodySlack = """

Hi Colleagues, 

There were issues detected with *DOMAIN DB*:

$checkStatusesSlack

Job <$JOB_URL | URL>
Job <$JOB_URL$BUILD_NUMBER/console | Console Output>
Job <https://wiki.wdf.sap.corp/wiki/display/JPaaS/DOMAIN+DB+SERVICE | legend>

Regards, 
Igor
""" 


                slackSend color: '#C01025', message: subjectTextSlack + bodySlack
                writeFile file: '/usr/sap/ljs/home/.jenkins/userContent/tmp/DomainDB.status', text: '1'
                fileInAlert= readFile '/usr/sap/ljs/home/.jenkins/userContent/tmp/DomainDB.status' 
                echo "File in alert has $fileInAlert"
                
            }
        }
    }
    stage("Send Recovery alert"){
            when{
                expression{
                    shouldSendResolvedAlert == true
                }
            }
            steps{
                // if status goes from Unstable or Failed >> Successfull
                script{
            def date = new Date()			
            def DATETIME_TAG = date.format("MMMM dd HH:mm")
            def subjectTextSlack = """IGOR: *[$WHERE]* *DOMAINDB* (Service/Dependency) pipeline *RECOVERED* at: $DATETIME_TAG UTC""".toString()
            def bodySlack = """

Hi Colleagues, 

Previously reported *DOMAINDB* (Service/Dependency) issues *RECOVERED*

$checkStatusesSlack

Job <$JOB_URL | URL>
Job <$JOB_URL$BUILD_NUMBER/console | Console Output>
Job <https://wiki.wdf.sap.corp/wiki/display/JPaaS/DOMAIN+DB+SERVICE | legend>

Regards, 
Igor
"""
            slackSend color: '#2EB67D', message: subjectTextSlack + bodySlack
            writeFile file: '/usr/sap/ljs/home/.jenkins/userContent/tmp/DomainDB.status', text: '0'
            fileInPost= readFile '/usr/sap/ljs/home/.jenkins/userContent/tmp/DomainDB.status' 
            echo "File in post has $fileInPost"
                    
                                
                    
                    }

                }
                    
            }
        }
    }

  
