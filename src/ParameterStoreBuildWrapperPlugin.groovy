import static TerraformValidateStage.ALL
import static TerraformEnvironmentStage.PLAN
import static TerraformEnvironmentStage.APPLY

class ParameterStoreBuildWrapperPlugin implements TerraformValidateStagePlugin, TerraformEnvironmentStagePlugin {
    private static globalPathPattern
    private static List globalParameterOptions = []
    private static defaultPathPattern = { options -> "/${options['organization']}/${options['repoName']}/${options['environment']}/" }

    public static void init() {
        TerraformEnvironmentStage.addPlugin(new ParameterStoreBuildWrapperPlugin())
        TerraformValidateStage.addPlugin(new ParameterStoreBuildWrapperPlugin())
    }

    public static withPathPattern(Closure newPathPattern) {
        globalPathPattern = newPathPattern
        return this
    }

    public static withGlobalParameter(String path, Map options = [:]) {
        globalParameterOptions << [path: path] + options
        return this
    }

    @Override
    public void apply(TerraformValidateStage stage) {
        globalParameterOptions.each { gp ->
            stage.decorate(ALL, addParameterStoreBuildWrapper(gp))
        }
    }

    @Override
    public void apply(TerraformEnvironmentStage stage) {
        def environment = stage.getEnvironment()
        List options    = getParameterOptions(environment)

        options.each { option ->
            stage.decorate(PLAN, addParameterStoreBuildWrapper(option))
            stage.decorate(APPLY, addParameterStoreBuildWrapper(option))
        }
    }

    List getParameterOptions(String environment) {
        List options = []
        options.add(getEnvironmentParameterOptions(environment))
        options.addAll(getGlobalParameterOptions())

        return options
    }

    List getGlobalParameterOptions() {
        return globalParameterOptions
    }

    Map getEnvironmentParameterOptions(String environment) {
        return [
            path: pathForEnvironment(environment),
            credentialsId: "${environment.toUpperCase()}_PARAMETER_STORE_ACCESS"
        ]
    }

    String pathForEnvironment(String environment) {
        String organization = Jenkinsfile.instance.getOrganization()
        String repoName     = Jenkinsfile.instance.getRepoName()
        def patternOptions  = [ environment: environment,
                                repoName: repoName,
                                organization: organization ]

        def pathPattern = globalPathPattern ?: defaultPathPattern

        return pathPattern(patternOptions)
    }

    public Closure addParameterStoreBuildWrapper(Map options = [:]) {
        def Map defaultOptions = [
            naming: 'basename'
        ]

        def parameterStoreOptions = defaultOptions + options

        return { closure ->
            withAWSParameterStore(parameterStoreOptions) {
                closure()
            }
        }
    }

    public static reset() {
        globalPathPattern = null
        globalParameterOptions = []
    }
}
