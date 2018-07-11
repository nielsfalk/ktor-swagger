# [ktor](https://github.com/Kotlin/ktor) with [swaggerUi](https://swagger.io/)

[![Build Status](https://travis-ci.com/nielsfalk/ktor-swagger.svg?branch=master)](https://travis-ci.com/nielsfalk/ktor-swagger)
[![Download](https://api.bintray.com/packages/ktor-swagger/maven-artifacts/ktor-swagger/images/download.svg) ](https://bintray.com/ktor-swagger/maven-artifacts/ktor-swagger/_latestVersion)

This project provides a library that allows you you to integrate the
 [swaggerUi](https://swagger.io/) with [ktor](https://github.com/Kotlin/ktor)

An example efrom the `ktor-sample-swagger` is deployed on [heroku](https://ktor-swagger.herokuapp.com/).

## Example Usage

This library adds some extension function that build on the ktor routing feature to provide an API
that allows this feature to automatically generate a `swagger.json` file for your webserver.

```kotlin
routing {
    get<pets>("all".responds(ok<PetsModel>())) {
        call.respond(data)
    }
    post<pets, PetModel>("create".responds(created<PetModel>())) { _, entity ->
        call.respond(Created, entity.copy(id = newId()).apply {
            data.pets.add(this)
        })
    }
    get<pet>("find".responds(ok<PetModel>(), notFound())) { params ->
        data.pets.find { it.id == params.id }
            ?.let {
                call.respond(it)
            }
    }
}
```

## Project Status

This project is a proof of concept built on a library to support this functionality.

There is an open proposal to include this project as an official Ktor feature
[here](https://github.com/ktorio/ktor/issues/453).
