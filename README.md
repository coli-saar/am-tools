# AM tools

Tools for converting between various graphbanks and AM dependency trees. They are intended for use in conjunction with the [AM dependency parser](https://github.com/coli-saar/am-parser), which see for documentation.


## Building am-tools

Compile the am-tools as follows:

```
./gradlew build
```

The `./gradlew` is for Mac and Linux; on Windows, use `gradlew` instead.

This should download a bunch of dependencies and finally create a file `build/lib/am-tools.jar`, which will be quite big because it contains pretrained models for the Stanford CoreNLP system.
