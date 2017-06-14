# ivyDEextension
This project is an extension to the eclipse-plugin [Apache IvyDE 2.2.0.final-201311091524-RELEASE](https://ant.apache.org/ivy/ivyde/) from the [Apache Software Foundation](https://www.apache.org/). ***So please have a look at the original [README](/README.txt)-, [LICENSE](/LICENSE)- and [NOTICE](/NOTICE) files***.

It is build on top of the latest ApacheIvyDE release (2.2.0.final) and extends its functionallity by providing the possibility to manage credentials - needed to retrieve artifacts from protected repositories - using the eclipse secure storage instead of setting them in plaintext within the ANT-buildscripts or ivySettings.

## 1. Compatability
This project tends to be compatible with the same versions as the source plugin is. Click [here](http://ant.apache.org/ivy/ivyde/history/trunk/compatibility.html) for further information. Compatabilities different to the ApacheIvyDE 2.2.0.final are listed below.

#### 1.1 Eclipse
The minimal required Eclipse version is Eclipse 4.2 (Juno).

#### 1.2 JVM
At least Java 1.6 is needed.

## 2. Installation
The installation procedure didn't change. Please click [here](http://ant.apache.org/ivy/ivyde/history/trunk/install.html) for further information.

## 3. Features
The Apache IvyDE-extension contains all the features of the 2.2.0.final version. In addition an additional preference page is provided (`Window > Preferences > Ivy > Security`) where a user can manage its ivy credentials inside the eclipse secure storage. This credentials are exported to the internal ivy-credential store and retrieved from there in case of a resolve/retrieve.

**NOTE:** The ivy-credentials stored this way are copied from the eclipse secure storage to the ivy credential store at the plugin startup or after they are inserted/updated/deleted. If somewhere credentials are provided the "usual" way (e.g. inside the ivySettings.xml, via ANT-Configuration), the credentials stored inside the eclipse secure storage may are overwritten. This occurs if credentials have the same host and realm (host@realm is used by ivy as identifier). To avoid such undesired behaviour please make sure that all your credentials are unique.

## 4. Usage
The usage of the Apache IvyDE-extension plugin remains the same. The only difference is the new preference page as described in ***3. Features***. The security preference page should be self-explaining.

## 5. Ivy issues
During the development the following issues with the used ivy-library were detected:

***1. Meaningless errors:*** If an ivy-resolve/retrieve fails because of not beeing authorized, the default console output is `BUILD FAILED: Artifact not found`. To discover the real error a developer has to dig deep inside the debug log.

***2. Final:*** Once credentials are stored inside the ivy-credential store, they are unchangable (final). Making them `final` makes it impossible to change them during runtime (e.g. to hook a credential-popup before the credentials are sent to the server).

***3. (Unpredictable) cache behaviour:*** If a resolve/retrieve fails because of invalid credentials, the credentials are reloaded on the next try. If one resolve/retrieve is successfull, the credentials are stored somewhere and changes on credentials inside the eclipse secure storage or the ivySettings/ANT-Configuration are completely ignored on subsequent resolves/retrieves, even the credentials are wrong. If Eclipse is restarted, the credentials are reloaded.

***4. Inflexible ivy-credential store:*** The ivy-credential store just offers a method to add credentials (to a hash-map) but no operation to remove them. In case of a removal, the add method has to be "abused" to invalidate the non-deletable credentials on the ivy-credential storage. To fully remove not needed credentials, Eclipse has to be restarted.
