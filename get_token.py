#!/usr/bin/python
import os
import sys
import requests
import time
from requests.adapters import HTTPAdapter
from requests.packages.urllib3.util.retry import Retry
import logging


USERNAME = os.environ["USERNAME"]
PASSWORD = os.environ["PASSWORD"]

#HTTP Adapter function to implement retries based on timeout and response code with incremental backoff

def requests_retry_session(
    retries=5,
    backoff_factor=0.3,
    status_forcelist=(500, 502, 503, 504),
    session=None,
):
    session = session or requests.Session()
    retry = Retry(
        total=retries,
        read=retries,
        connect=retries,
        backoff_factor=backoff_factor,
        status_forcelist=status_forcelist,
        method_whitelist=["HEAD", "GET", "PUT", "DELETE", "OPTIONS", "TRACE", "POST"]
    )
    adapter = HTTPAdapter(max_retries=retry)
    session.mount('http://', adapter)
    session.mount('https://', adapter)
    return session

logging.basicConfig()
logging.getLogger().setLevel(logging.DEBUG)
requests_log = logging.getLogger("requests.packages.urllib3")
requests_log.setLevel(logging.DEBUG)
requests_log.propagate = True
auth_url = "https://uaa.cf.us10.hana.ondemand.com/oauth/token" # <= US10
#auth_url = "https://httpbin.org/status/500" # <= US10

grant_type = "password"
avs_status = None

data = {
    "username": USERNAME,
    "password": PASSWORD,
    "grant_type": grant_type
}

headers = {
  "accept": "application/json",
  "Content-Type": "application/x-www-form-urlencoded",
  "Authorization": "Basic Y2Y6"
}

t0 = time.time()
try:
    response = requests_retry_session().post(auth_url, data=data, headers=headers, timeout=2)
    response.raise_for_status()
    token = response.json()
    try:
        only_token = token['access_token']
        bearer = 'Bearer ' + only_token
    except NameError:
        pass


except requests.exceptions.HTTPError as err:
    raise SystemExit(err)

except requests.exceptions.RetryError as errr:
    print ("ERROR: Too Many retries failed.",errr)

except requests.exceptions.ConnectionError as errc:
    print ("ERROR: Error Connecting:",errc)

except requests.exceptions.Timeout as errt:
    print ("ERROR: Timeout Error:",errt)

except Exception as x:
    print( "ERROR: Failed to get Token after all attempts:", x.__class__.__name__)

else:
    print( 'INFO: Successfully obtained token. Status code:', response.status_code)
finally:
    #print(only_token)
    try:
        os.environ["AVS_TOKEN"] = only_token
        #print(os.environ["AVS_TOKEN"])
        t1 = time.time()
        print('Took', t1 - t0, 'seconds')
    except NameError:
        pass
