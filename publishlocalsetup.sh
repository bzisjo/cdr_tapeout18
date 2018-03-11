#!/bin/bash
DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
# DIR=$DIR/..
if [ ! -d "$DIR/firrtl" ]; then
  git clone https://github.com/freechipsproject/firrtl.git
fi

if [ ! -d "$DIR/firrtl-interpreter" ]; then
  git clone https://github.com/freechipsproject/firrtl-interpreter.git
fi

if [ ! -d "$DIR/chisel3" ]; then
  git clone https://github.com/freechipsproject/chisel3.git
fi

if [ ! -d "$DIR/chisel-testers" ]; then
  git clone https://github.com/freechipsproject/chisel-testers.git
fi

if [ ! -d "$DIR/dsptools" ]; then
  https://github.com/ucb-bar/dsptools.git
fi

cd $DIR/firrtl/
git pull --rebase
git checkout v1.1.0-RC1
sbt clean
sbt compile
sbt assembly
sbt publishLocal

cd $DIR/firrtl-interpreter
git pull --rebase
git checkout v1.1.0-RC1
sbt clean
sbt compile
sbt publishLocal

cd $DIR/chisel3
git pull --rebase
git checkout v3.1.0-RC1
sbt clean
sbt compile
sbt publishLocal

cd $DIR/chisel-testers
git pull --rebase
git checkout v1.2.0-RC1
sbt clean
sbt compile
sbt publishLocal

cd $DIR/dsptools
git pull --rebase
git checkout v1.1.0-RC1
sbt clean
sbt compile
sbt publishLocal
