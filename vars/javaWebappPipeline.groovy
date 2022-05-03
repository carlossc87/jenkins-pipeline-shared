/*
Parámetros:
    id - Nombre del proyecto
    context - Contexto a desplegar en tomcat
    branch - Rama a obtener
    scmurl - Url del repositorio
    email - Dirección de correo a donde enviar los mensajes
*/
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent any

        parameters {
            booleanParam defaultValue: true, description: '¿Analizar con Sonar?', name: 'SONAR'
        }

        stages {
            stage('Checkout') {
                steps {
                    echo 'Obteniendo código fuente'
                    git branch: pipelineParams.branch, url: pipelineParams.scmurl
                }
            }
            stage('Build'){
                steps {
                    echo 'Contruyendo'
                    sh "mvn clean package -DskipTests"
                }
            }
            stage('Test'){
                steps {
                    echo 'Pruebas unitarias'
                    sh "mvn test"
                }
            }
            stage('Init Parallel') {
                parallel {
                    stage('Analyze'){
                        when {
                            expression { 
                            return params.SONAR
                            }
                        }
                        steps {
                            echo 'Analizando código fuente'
                            withCredentials([usernamePassword(credentialsId: 'sonar', usernameVariable: 'SONAR_USER', passwordVariable: 'SONAR_PASS')]) {
                                sh 'mvn sonar:sonar -Dsonar.host.url=$sonar_host -Dsonar.projectKey=$pipelineParams.id -Dsonar.projectName=$pipelineParams.id -Dsonar.login=$SONAR_USER -Dsonar.password=$SONAR_PASS'
                            }
                        }
                    }
                    stage('Deploy'){
                        steps {
                            echo 'Desplegando'
                            withCredentials([usernamePassword(credentialsId: 'tomcat', usernameVariable: 'TOMCAT_USER', passwordVariable: 'TOMCAT_PASS')]) {
                                sh 'mvn org.apache.tomcat.maven:tomcat7-maven-plugin:2.1:redeploy-only -Dmaven.tomcat.url=$tomcat_manager -Dmaven.tomcat.path=/$pipelineParams.context -Dtomcat.username=$TOMCAT_USER -Dtomcat.password=$TOMCAT_PASS'
                            }
                        }
                    }
                }
            }
        }
        post {
            success {
                emailext body: 'La tarea $JOB_NAME se ha ejecutado correctamente',
                        subject: '$JOB_NAME OK :)',
                        to: '$pipelineParams.email' 
            }
            failure {
                emailext body: 'Ha fallado la ejecución de la tarea $JOB_NAME',
                        subject: '$JOB_NAME KO :(',
                        to: '$pipelineParams.email'  
            }
        }
    }
}