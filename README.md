# covid-report

Available at https://covid.barneyb.com/

Basically a toy to do everything the wrong way as a means to enlightenment.
Database? Bah! Templates? Bah! UI toolkit? Bah!

Unclear what I've enlightened, but certainly occupied quite a number of house
without resorting to thumb-twiddling.

## Building

Get yourself a modern-ish Maven and Java. I've got this:

    $ mvn --version
    Apache Maven 3.6.0
    Maven home: /usr/share/maven
    Java version: 11.0.7, runtime: /usr/lib/jvm/java-11-openjdk-amd64

If you've something similar, `mvn package` will spit out a nice JAR file for
you. Next, you'll need the CSSE data from https://github.com/CSSEGISandData/COVID-19,
and to register it in `application.yaml` (or better, use`-Dcovid-report.hopkins.dir`).
Then run `java --jar covid-report.jar --hopkins` and you'll end up with a nice
data directory full of JSON files.

The client (in `src/main/webapp`) is entirely static and needs only to have a `data`
subdirectory present w/ the output of the Java command. There's none of that
"modern" JS BS. I'm a data guy, not a marketing guy. I like things that work.
Not work-ish. Work. If you're hoping for IE support, look elsewhere. IE doesn't
work. :)
