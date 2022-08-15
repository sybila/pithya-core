# Download base image ubuntu 20.04
FROM ubuntu:20.04
MAINTAINER Matej Trojak <xtrojak@fi.muni.cz>

# Update packages
RUN apt-get update

# Install Java and z3
RUN apt-get -y install z3 openjdk-8-jdk

# Install Pithya
COPY . pithya/
WORKDIR pithya
RUN ./gradlew installDist
ENV PATH="/pithya/build/install/pithya/bin:$PATH"