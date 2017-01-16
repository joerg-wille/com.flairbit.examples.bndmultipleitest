A couple of completely nonsensical integration tests to reproduce a problem in bnd-testing-maven-plugin with multiple integration tests in the same maven project.

----
The problem is the following:

The project contains two modules with one integration test each. Both modules are specified in their parent pom.

* `mvn install` for the first module runs OK:

```
cd com.flairbit.itest1 && mvn install && cd -
```
* `mvn install` for the second module runs OK:

```
cd com.flairbit.itest2 && mvn install && cd -
```
* **Running both tests from parent pom fails**

```
mvn install
```
