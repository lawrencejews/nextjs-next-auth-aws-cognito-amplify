package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.amplify.alpha.*;
import software.amazon.awscdk.services.amplify.alpha.App;
import software.amazon.awscdk.services.codebuild.BuildSpec;
import software.amazon.awscdk.services.cognito.*;
import software.constructs.Construct;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class AwsCdkStack extends Stack {
    public AwsCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AwsCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        UserPool userPool = UserPool.Builder.create(this, "next-userpool")
                .userPoolName("next-userpool")
                .removalPolicy(RemovalPolicy.DESTROY)
                .signInAliases(SignInAliases.builder()
                        .email(true)
                        .build())
                .selfSignUpEnabled(true)
                .autoVerify(AutoVerifiedAttrs.builder()
                        .email(true)
                        .build())
                .userVerification(UserVerificationConfig.builder()
                        .emailSubject("Please verify your demo email")
                        .emailBody("Thanks for your registration! Your code is {####}")
                        .emailStyle(VerificationEmailStyle.CODE)
                        .build())
                .standardAttributes(StandardAttributes.builder()
                        .email(StandardAttribute.builder()
                                .required(true)
                                .mutable(true)
                                .build())
                        .familyName(StandardAttribute.builder()
                                .required(false)
                                .mutable(false)
                                .build())
                        .build())
                .customAttributes( Map.of(
                        "created_at", new DateTimeAttribute()
                ))
                .build();

        UserPoolClient client = userPool.addClient("next-userpoolclient", UserPoolClientOptions.builder()
                        .userPoolClientName("next-userpoolclient")
                        .generateSecret(false)
                        .authFlows(AuthFlow.builder()
                                .userSrp(true)
                                .userPassword(true)
                                .build())
                .build());

        userPool.addDomain("lawrencejewsdomain", UserPoolDomainOptions.builder()
                        .cognitoDomain(CognitoDomainOptions.builder()
                                .domainPrefix("lawrencejewsdomain")
                                .build())
                .build());

        CfnOutput.Builder.create(this, "COGNITO_ID").value(userPool.getUserPoolId()).build();
        CfnOutput.Builder.create(this, "COGNITO_CLIENT_ID").value(client.getUserPoolClientId()).build();
        // issue URL

        App amplifyApp = App.Builder.create(this, "demo-amplify-hosting")
                .appName("demo-amplify-hosting")
                .sourceCodeProvider(GitHubSourceCodeProvider.Builder
                        .create()
                        .owner("lawrencejews")
                        .repository("nextjs-next-auth-aws-cognito-amplify")
                        .oauthToken(SecretValue.secretsManager("demo-amplify-hosting"))
                        .build())
                .autoBranchDeletion(true)
                .platform(Platform.WEB_COMPUTE)  // Set to web compute if SSR
                .buildSpec(BuildSpec.fromObjectToYaml(
                        new LinkedHashMap<>(){{ // To preserve the order of items entered
                            put("version", "1.0");
                            put("applications", List.of(
                                new LinkedHashMap<>() {{
                                    put("appRoot", "next-demo-auth");
                                    put("frontend", new LinkedHashMap<>() {{
                                        put("buildPath", "next-demo-auth");
                                        put("phases", new LinkedHashMap<>(){{
                                            put("preBuild", new LinkedHashMap<>(){{
                                                put("commands", List.of(
                                                        "npm ci"
                                                ));  //clean install
                                            }});
                                            put("build", new LinkedHashMap<>(){{
                                                put("commands", List.of(
                                                        "npm run build",
                                                        "echo \"NEXTAUTH_SECRET=lawrencejews2433\" >> .env.production",
                                                        """
                                                                if["$AWS_BRANCH" = "main"]; then
                                                                    "echo "NEXTAUTH_URL=https://main.${AWS_APP_ID}.amplifyapp.com/" >> .env.production"
                                                                elif["$AWS_BRANCH" = "dev"]; then
                                                                    "echo "NEXTAUTH_URL=https://dev.${AWS_APP_ID}.amplifyapp.com/" >> .env.production"
                                                                fi
                                                         """,
                                                        "echo \"COGNITO_ID="+userPool.getUserPoolId()+"\" >> .env.production",
                                                        "echo \"COGNITO_CLIENT_ID="+client.getUserPoolClientId()+"\" >> .env.production"

                                                ));  // set postBuild if needed
                                            }});
                                        }});
                                        put("artifacts", new LinkedHashMap<>(){{
                                            put("files", List.of("**/*"));
                                            put("baseDirectory", ".next");
                                        }});
                                        put("cache", new LinkedHashMap<>(){{
                                            put("paths", List.of("node_modules/**/*", ".next/cache/**/*"));
                                        }});
                                    }});
                                }}
                            ));
                        }}
                ))
                .build();
        amplifyApp.addCustomRule(CustomRule.Builder.create()
                        .source("/<*>")
                        .target("/index.html")
                        .status(RedirectStatus.NOT_FOUND_REWRITE)
                .build());
        amplifyApp.addEnvironment("COGNITO_ID", userPool.getUserPoolId())
                .addEnvironment("COGNITO_CLIENT_ID", client.getUserPoolClientId())
                .addEnvironment("_CUSTOM_IMAGE", "amplify:al2023") // NEXTJS 14 Support for custom build
//                .addEnvironment("_CUSTOM_IMAGE", "Amazon Linux: 2023")
                .addEnvironment("LIVE UPDATES", "[{\"pkg\":\"next-version\",\"type\":\"internal\", \"version\":\"latest\"}]")
                .addEnvironment("AMPLIFY_MONOREPO_APP_ROOT", "next-demo-auth");
        Branch main = amplifyApp.addBranch("main", BranchOptions.builder()
                        .stage("PRODUCTION")
                .build());
        Branch dev = amplifyApp.addBranch("dev", BranchOptions.builder()
                .stage("DEVELOPMENT")
                        .performanceMode(true)
                .build());
    }
}
