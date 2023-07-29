[![Release](https://jitpack.io/v/0xZhangKe/KRouter.svg)](https://jitpack.io/#0xZhangKe/KRouter)

# KRouter
Find interface implementation by uri string.

For Kotlin module to module communication.

The main purpose is to open the compose screen from other modules(e.g. [Voyager](https://voyager.adriel.cafe/navigation)).

Base on ServiceLoader, KSP and Kotlin reflect.

# Usage
First, define a interface.
```kotlin
interface Screen
```
After that, define some implementation.
```kotlin
@Destination("screen/home")
class HomeScreen(@Router val router: String = "") : Screen

@Destination("screen/profile")
class ProfileScreen : Screen {
    @Router
    lateinit var router: String
}

```
This implementation can be distributed to any modules.

Now, you can route to any Screen by router.
```kotlin
val homeScreen = KRouter.route<Screen>("screen/home?name=zhangke")
val profileScreen = KRouter.route<Screen>("screen/profile?name=zhangke")
```
As show above, you will get homeScreen and router property is `screen/home?name=zhangke`.

See the [sample.app](https://github.com/0xZhangKe/KRouter/tree/main/sample/app/src/main/java/com/zhangke/kouter/sample/app) module for a more detailed example.

## Integration
Firstly, you must set up [KSP](https://kotlinlang.org/docs/ksp-overview.html) in your project.
Then, add KRouter dependency in module.
```kts
// module`s build.gradle.kts
implementation(project(":core"))
ksp(project(":compiler"))
```
Additionally, add resources dir in source sets.
```
// module`s build.gradle.kts
kotlin {
    sourceSets.main {
        resources.srcDir("build/generated/ksp/main/resources")
    }
}
```

## @Destination
Destination annotation is defined for a route Destination.
It`s have two parameters:
- route: This destination`s identify route, must be uri string.
- type: Which interface or abstract class this destination for, ignore this if just have single super type.

## @Router
This annotation is used to identify which property is used to accept the route.
So, this property must be a class`s variable property or constructor parameter.
The router is passed into this field when the destination object is constructed.
