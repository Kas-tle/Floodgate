plugins {
    // allow resolution of compileOnlyApi dependencies in Eclipse
    id("eclipse")
}

eclipse {
    classpath {
    	configurations.compileOnlyApi.get().setCanBeResolved(true)
        plusConfigurations.add(configurations.compileOnlyApi.get())
   	}
}