#!/usr/bin/env bash
# The Jenkins library's backend security.sh, reading the endpoints from the repo's OpenAPI spec
# rather than /v2/api-docs. The library runs this in place of its own, with the workspace at /zap/wrk.
echo ${TEST_URL}

if [[ -z ${SecurityRules} ]]; then
  echo "SecurityRules variable not set. Please set it as a parameter in your Jenkinsfile_nightly file."
  exit 1
fi

zap-api-scan.py -t infrastructure/apim/specs/api-marketplace.openapi.yml -O ${TEST_URL} -f openapi -S -d -u ${SecurityRules} -P 1001 -z "-config rules.cookie.ignorelist=_ga,_gid,_gat,dtCookie,dtLatC,dtPC,dtSa,rxVisitor,rxvt" -l FAIL -r /zap/wrk/api-report.html
curl --fail http://0.0.0.0:1001/OTHER/core/other/jsonreport/?formMethod=GET --output report.json
export LC_ALL=C.UTF-8
export LANG=C.UTF-8
mkdir -p functional-output
chmod a+wx functional-output
cp /zap/wrk/api-report.html functional-output/
cp *.* functional-output/
