/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com)
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.flowmodelgenerator.extension;

import com.google.gson.Gson;
import io.ballerina.cli.service.CliToolService;
import io.ballerina.cli.service.ToolName;
import io.ballerina.cli.service.types.CommandResponse;
import io.ballerina.cli.service.types.Status;
import io.ballerina.flowmodelgenerator.core.OpenAPIClientGenerator;
import io.ballerina.flowmodelgenerator.extension.request.OpenAPIClientDeleteRequest;
import io.ballerina.flowmodelgenerator.extension.request.OpenAPIClientGenerationRequest;
import io.ballerina.flowmodelgenerator.extension.request.OpenAPIGeneratedModulesRequest;
import io.ballerina.flowmodelgenerator.extension.response.OpenAPIClientDeleteResponse;
import io.ballerina.flowmodelgenerator.extension.response.OpenAPIClientGenerationResponse;
import io.ballerina.flowmodelgenerator.extension.response.OpenAPIGeneratedModulesResponse;
import io.ballerina.tools.text.LSPTextEdit;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService;
import org.ballerinalang.langserver.commons.workspace.WorkspaceManager;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.eclipse.lsp4j.services.LanguageServer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")
@JsonSegment("openAPIService")
public class OpenAPIService implements ExtendedLanguageServerService {

    private final Gson gson = new Gson();

    @Override
    public void init(LanguageServer langServer, WorkspaceManager workspaceManager) {
    }

    @Override
    public Class<?> getRemoteInterface() {
        return null;
    }

    @JsonRequest
    public CompletableFuture<OpenAPIClientGenerationResponse> genClient(OpenAPIClientGenerationRequest req) {
        return CompletableFuture.supplyAsync(() -> {
            OpenAPIClientGenerationResponse response = new OpenAPIClientGenerationResponse();
            try {
                OpenAPIClientGenerator openAPIClientGenerator =
                        new OpenAPIClientGenerator(Path.of(req.openApiContractPath()), Path.of(req.projectPath()));
                response.setSource(openAPIClientGenerator.genClient(req.module()));
            } catch (Throwable e) {
                response.setError(e);
            }
            return response;
        });
    }

    @JsonRequest
    public CompletableFuture<OpenAPIClientGenerationResponse> genClientV2(OpenAPIClientGenerationRequest req) {
        return CompletableFuture.supplyAsync(() -> {
            OpenAPIClientGenerationResponse response = new OpenAPIClientGenerationResponse();
            try {
                String command = "add";
                List<String> arguments = List.of(req.openApiContractPath());
                Map<String, Object> context = Map.of("projectPath", req.projectPath());

                ServiceLoader<CliToolService> services = ServiceLoader.load(CliToolService.class,
                        Thread.currentThread().getContextClassLoader());

                CliToolService service = services.stream()
                        .map(ServiceLoader.Provider::get)
                        .filter(OpenAPIService::isOpenApiToolService)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("OpenAPI tool service not found"));

                CommandResponse result = service.executeCommand(command, arguments.toArray(new String[0]), context);
                if (result.status().equals(Status.FAILURE)) {
                    throw new RuntimeException("Error generating client: " + result.errors());
                }

                Map<Path, List<TextEdit>> textEdits = new HashMap<>();
                Map<String, List<LSPTextEdit>> stringListMap = result.textEdits();
                for (Map.Entry<String, List<LSPTextEdit>> entry : stringListMap.entrySet()) {
                    Path path = Path.of(entry.getKey());
                    List<TextEdit> edits = entry.getValue().stream()
                            .map(CommonUtil::toTextEdit)
                            .toList();
                    textEdits.put(path, edits);
                }
                OpenAPIClientGenerator.ClientSource clientSource = new OpenAPIClientGenerator.ClientSource(
                        false, textEdits);
                response.setSource(gson.toJsonTree(clientSource));
            } catch (Throwable e) {
                response.setError(e);
            }
            return response;
        });
    }

    private static boolean isOpenApiToolService(CliToolService service) {
        return service.getClass().getAnnotation(ToolName.class) != null &&
                service.getClass().getAnnotation(ToolName.class).value().equals("openapi");
    }

    @JsonRequest
    public CompletableFuture<OpenAPIGeneratedModulesResponse> getModules(OpenAPIGeneratedModulesRequest req) {
        return CompletableFuture.supplyAsync(() -> {
            OpenAPIGeneratedModulesResponse response = new OpenAPIGeneratedModulesResponse();
            try {
                OpenAPIClientGenerator openAPIClientGenerator =
                        new OpenAPIClientGenerator(null, Path.of(req.projectPath()));
                response.setModules(openAPIClientGenerator.getModules());
            } catch (Throwable e) {
                response.setError(e);
            }
            return response;
        });
    }

    @JsonRequest
    public CompletableFuture<OpenAPIClientDeleteResponse> deleteModule(OpenAPIClientDeleteRequest req) {
        return CompletableFuture.supplyAsync(() -> {
            OpenAPIClientDeleteResponse response = new OpenAPIClientDeleteResponse();
            try {
                OpenAPIClientGenerator openAPIClientGenerator =
                        new OpenAPIClientGenerator(null, Path.of(req.projectPath()));
                response.setDeleteData(openAPIClientGenerator.deleteModule(req.module()));
            } catch (Throwable e) {
                response.setError(e);
            }
            return response;
        });
    }
}
