#sbt -Dtests.threads=1,4 -Dtests.targets=scala -DshowSuppressedErrors=true "; project asplos-apps; compile"

sbt -Dtests.threads=1 -Dtests.targets=scala "; project feattest; test" | tee test.log
