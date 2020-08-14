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
        checkInAvsFailed2 = false
        checkInDirectFailed = false
        checkInDirectFailed2 = false
        checkInThousandEyes = false
        checkInMetrics = false
        checkInMetrics2 = false
        checkSInAvSIAS =  false
        checkSInDirectIAS =  false
        
        //python variables
        EVAL_DATA = '$ENV_EVAL_DATA'
        DIRECT_URL = '$ENV_DIRECT_URL'

        EVAL_DATA2 = '$ENV_EVAL_DATA2'
        DIRECT_URL2 = '$ENV_DIRECT_URL2'

        EVAL_IAS = '$ENV_EVAL_IAS'
        DIRECT_IAS = '$ENV_DIRECT_IAS'
        
		//Java for Variables General
        DC = '$WHERE'
		
		AUTH = credentials('puser')
        //USERNAME = '$sshUser'
        //PASSWORD = '$sshPass'

		//Java Variables for profiling logs
		CHECK_NAME = 'DEPLOY_OPERATION'
		
    }

    stages {

        stage('Checking monitor in AvS and directly - SSO Endopoint Healthcheck') {
            steps {

                script {
                    try {
                        CHECK_IN_AVS = sh(script: "python $ENV_JOBS/avs_requests.py $EVAL_DATA $DIRECT_URL", returnStdout: true).toString().trim()
                        echo CHECK_IN_AVS
                        if (CHECK_IN_AVS.toString().contains("DOWN")) {
                            checkInAvsFailed = true
                            checkStatuses = checkStatuses.concat("1. AvS status for SSO Endpoint: DOWN\n")
                            unstable("AvS status for SSO Endpoint: DOWN")
                        }

                        else {

                            checkStatuses = checkStatuses.concat("1. AvS status for SSO Endpoint: UP\n")
                        }

                        if (CHECK_IN_AVS.toString().contains("500")) {

                            echo "Looking for 500"
                            checkInDirectFailed = true
                            checkStatuses = checkStatuses.concat("1. Direct check for SSO Endpoint: DOWN\n")
                            unstable("Direct check status is DOWN with 500")

                        } else {
                            echo "SSO endpoint is up!"
                            checkStatuses = checkStatuses.concat("1. Direct check status for SSO Endpoint: UP\n")
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


		stage('Checking monitor in AvS and directly - SSO Basic Authentication') {
            steps {
                script {
                    try {
                        CHECK_IN_AVS = sh(script: "python $ENV_JOBS/avs_requests.py $EVAL_DATA2 $DIRECT_URL2", returnStdout: true).toString().trim()
                        echo CHECK_IN_AVS
                        if (CHECK_IN_AVS.toString().contains("DOWN")) {
                            checkInAvsFailed2 = true
                            checkStatuses = checkStatuses.concat("2. AvS status for Basic Auth: DOWN\n")
                            unstable("Check status in AVS is DOWN")
                        }

                        else {

                            checkStatuses = checkStatuses.concat("2. AvS status for Basic Auth: UP\n")
                        }

                        if (CHECK_IN_AVS.toString().contains("500")) {

                            echo "Looking for 500"
                            checkInDirectFailed2 = true
                            checkStatuses = checkStatuses.concat("2. Direct status for Basic Auth: DOWN\n")
                            unstable("Direct check status is DOWN")

                        } else {
                            echo "Basic authentication is working!"
                            checkStatuses = checkStatuses.concat("2. Direct status for Basic Auth: UP\n")
                            checkInDirectFailed2 = false

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
		   // when {
			   // expression {
				   // checkInAvsFailed == true || checkInAvsFailed2 == true

			   // }
		  //  }
			steps {
				script {
					try {
						CHECK_THOUSAND_EYES = (sh(script: "cat $ENV_JOBS/ThousandEyes/\"$WHERE\".json ", returnStdout: true).toString().trim())
						echo CHECK_THOUSAND_EYES
						if (CHECK_THOUSAND_EYES.toString().contains("active")) {
							checkStatuses = checkStatuses.concat("3. ThousandEyes alert is active for this landscape: BAD \n")
							unstable("There are active alerts for this landscape in ThousandEyes")
							checkInThousandEyes = true
						} else {
						echo "3. No ThousandEyes alerts active for this landscape: OK "
						checkStatuses = checkStatuses.concat("3. No ThousandEyes active alerts for this landscape: OK \n")
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
                   (checkInAvsFailed == true && checkInDirectFailed == true && checkInThousandEyes == true) || (checkInAvsFailed2 == true && checkInDirectFailed2 == true && checkInThousandEyes == true)
                }
            }
            steps {
                script {
                    def bodymail = """

This email is sent as there are tree conditions met:
1. AvS has showed status DOWN for one of the checks.
2. Direct check to $DIRECT_URL or $DIRECT_URL2 is down.
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
                                subject: "IGOR: OUTAGE DETECTED (NETWORK)  ${WHERE} $JOB_NAME ${formattedDate} UTC",
                                body: "Hi Colleagues,\n\nHere is an automated report from Igor pipeline that caught some issues in SSO: \n\n"+ checkStatuses + bodymail + "\n"  + "\nRegards, \nIgor"
                        echo "Mail Sent to " + toAddress


                }
            }
        }


		stage("Check SSO service machines State and Metrics") {
			//when {
			  //  expression {
				//    checkInAvsFailed == true || checkInAvsFailed2 == true

				//}
			//}
			steps {
				script {
					try {
						CHECK_METRICS_SSO = sh(script: "cat $ENV_JOBS/SSO_Service/metrics.txt", returnStdout: true).toString().trim()
						CHECK_STATE_SSO = sh(script: "cat $ENV_JOBS/SSO_Service/state.txt", returnStdout: true).toString().trim()
						echo CHECK_METRICS_SSO
						if (CHECK_METRICS_SSO.toString().contains("CRITICAL") || CHECK_METRICS_SSO.toString().contains("Critical")) {
							checkInMetrics = true
							checkStatuses = checkStatuses.concat("4. SSO machines metrics contains CRITICAL: BAD!\n")
							unstable("SSO machines metrics contains CRITICAL. ")
						} else {
							echo "SSO machines metrics OK."
							checkStatuses = checkStatuses.concat("4. SSO machines metrics does not contain CRITICAL: OK! \n")
						}
						echo CHECK_STATE_SSO
						if (CHECK_STATE_SSO.toString().contains("CRITICAL") || CHECK_STATE_SSO.toString().contains("Critical")) {
							checkInMetrics2 = true
							checkStatuses = checkStatuses.concat("5. SSO machines state contains CRITICAL: BAD!\n")
							unstable("SSO machines state contains CRITICAL. ")
						} else {
							echo "SSO machines state does not contains CRITICAL."
							checkStatuses = checkStatuses.concat("5. SSO machines state does not contains CRITICAL: OK! \n")
						}
					} catch (err) {
						echo err.getMessage()
					}
				}
			}
		}


        stage('Check IAS in AvS and Directly') {
            steps {
                script {
                    try {
						CHECK_IN_AVS_IAS = sh(script: "python $ENV_JOBS/avs_requests.py  $EVAL_IAS $DIRECT_IAS", returnStdout: true).toString().trim()
						echo CHECK_IN_AVS_IAS
						if (CHECK_IN_AVS_IAS.toString().contains("DOWN")) {
							checkSInAvSIAS = true
							checkStatuses = checkStatuses.concat("6. AvS IAS status: DOWN\n")
							unstable("Check status in AVS is DOWN")
						}
						else {
							checkStatuses = checkStatuses.concat("6. AvS IAS status: UP\n")
						}

						if (CHECK_IN_AVS_IAS.toString().contains("500")) {							
							checkSInDirectIAS = true
							checkStatuses = checkStatuses.concat("6. Direct status IAS: DOWN\n")
							unstable("Direct check status is DOWN")
						} else {
							echo "Did not found any issues in AvS or Direct for IAS. All good"
							checkStatuses = checkStatuses.concat("6. irect status IAS: UP\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }
		
		
		stage('Mail Alert: OUTAGE - Service issues') {
            when {
                expression {
                    ((checkInAvsFailed == true && checkInDirectFailed == true) || (checkInAvsFailed2 == true && checkInDirectFailed2 == true)) && (checkInThousandEyes == true || checkInMetrics == true || checkInMetrics2 == true || checkSInAvSIAS == true || checkSInDirectIAS == true)
                }
            }
            steps {
                script {
                    def bodymail = """


The pipeline consists of the following stages:

1. AvS and Direct check for SSOendpoint
2. AvS and Direct check for SSOendpoint Basic authentication
3. ThousandEyes check for the landscape
4. SSO VMs metrics contains CRITICAL
5. SSO VMs state is CRITICAL
6. AvS and Direct check for IAS

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
                                subject: "IGOR: OUTAGE DETECTED with SSO on ${WHERE}:  ${formattedDate} UTC",
                                body: "Hi Colleagues,\n\nHere is an automated report from Igor pipeline that caught some issues in SSO: \n\n"+ checkStatuses + bodymail + "\n"  + "\nRegards, \nIgor"
                        echo "Mail Sent to " + toAddress
                }
            }
        }
	}
}


