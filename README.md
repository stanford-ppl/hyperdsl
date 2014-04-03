hyperdsl
=====
This is a repository containing all of the latest code dependencies for the Delite Compiler Framework, an interactive framework to rapidly and effeciently create DSL's.  

Three repositories are submodules in this framework.  See each individual repository for further detail:

1) Forge: https://github.com/stanford-ppl/Forge
2) Delite: https://github.com/stanford-ppl/Delite
3) LMS: https://github.com/TiarkRompf/virtualization-lms-core

Installation
============

    cd hyperdsl
    git submodule update -init
    sbt compile

Updating Existing Repo to Latest Commits
============

    cd hyperdsl
    git pull
    git submodule update
    sbt compile

More information
============
The hyperdsl repository points to a specific commit of each of the three repositories above that are known to work with each other.  If you 'cd' into any of these repositories and enter `git status` you will notice this.  If you want to checkout a specific branch of a submodule you can do so and operate on that branch in a normal fashion.  Please see http://blog.jacius.info/git-submodule-cheat-sheet/ for more information on working with git submodules.
