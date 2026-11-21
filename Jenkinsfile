// =====================================================================================
//  Mukono NGO food distribution app — Jenkins Multibranch Pipeline
//  Task 8: Firebase Performance Monitoring
//
//  Every branch and pull request with this file gets its own job automatically.
//    PR-*       : unit tests + lint + debug APK (perf bytecode instrumentation off = faster)
//    branches   : same, with Firebase Performance instrumentation ON, so the APK that
//                 Jenkins archives reports automatic network/screen traces as well as the
//                 custom journey traces.
//
//  Agent         : label "android", JDK tool "jdk21", Android SDK (ANDROID_HOME)
//  Credential    : mukono-google-services-json  (Secret file = app/google-services.json)
// =====================================================================================

pipeline {
    agent { label 'android' }

    tools {
        jdk 'jdk21'   // Manage Jenkins > Tools > JDK installations, name must match
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '5'))
        timestamps()
        timeout(time: 40, unit: 'MINUTES')
        disableConcurrentBuilds(abortPrevious: true)
    }

    environment {
        PERF_INSTRUMENTATION = "${env.CHANGE_ID ? 'false' : 'true'}"
        // The APK downloads beneficiary/manifest JSON from the branch it was built from
        FIELD_DATA_REF = "${env.CHANGE_ID ? 'main' : (env.BRANCH_NAME ?: 'main')}"
    }

    stages {

        stage('Environment') {
            steps {
                gradlew('--version')
            }
        }

        stage('Unit Tests') {
            steps {
                withFirebaseConfig {
                    gradlew("testDebugUnitTest ${perfFlags()}")
                }
            }
            post {
                always {
                    // Includes TraceNamesTest: fails the build if a trace/metric/attribute
                    // name breaks Firebase's naming rules (they would be silently dropped).
                    junit testResults: 'app/build/test-results/testDebugUnitTest/*.xml', allowEmptyResults: false
                }
            }
        }

        stage('Lint') {
            steps {
                withFirebaseConfig {
                    gradlew("lintDebug ${perfFlags()}")
                }
            }
            post {
                always {
                    recordIssues(
                            tools: [androidLintParser(pattern: 'app/build/reports/lint-results-debug.xml')],
                            qualityGates: [[threshold: 1, type: 'TOTAL_ERROR', criticality: 'UNSTABLE']])
                }
            }
        }

        stage('Build APK') {
            steps {
                withFirebaseConfig {
                    gradlew("assembleDebug ${perfFlags()}")
                }
            }
            post {
                success {
                    archiveArtifacts artifacts: 'app/build/outputs/apk/debug/*.apk', fingerprint: true
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'app/build/reports/**', allowEmptyArchive: true
        }
        cleanup {
            cleanWs()   // also removes the copied google-services.json
        }
    }
}

// ---- helpers (work on Linux and Windows agents) -------------------------------------

def perfFlags() {
    return "-PfirebasePerformanceInstrumentationEnabled=${env.PERF_INSTRUMENTATION} -PfieldDataRef=${env.FIELD_DATA_REF}"
}

def gradlew(String args) {
    if (isUnix()) {
        sh 'chmod +x gradlew'
        sh "./gradlew --no-daemon --stacktrace ${args}"
    } else {
        bat "gradlew.bat --no-daemon --stacktrace ${args}"
    }
}

def withFirebaseConfig(Closure body) {
    withCredentials([file(credentialsId: 'mukono-google-services-json', variable: 'GOOGLE_SERVICES_JSON')]) {
        body()
    }
}
