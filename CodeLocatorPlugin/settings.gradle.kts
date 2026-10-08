rootProject.name = "CodeLocatorPlugin"

include(":CodeLocatorModel")

if (System.getProperty("os.name").lowercase().indexOf("windows") != -1) {
    project(":CodeLocatorModel").projectDir = File("..\\CodeLocatorApp\\CodeLocatorModel")
} else {
    project(":CodeLocatorModel").projectDir = File("../CodeLocatorApp/CodeLocatorModel")
}
