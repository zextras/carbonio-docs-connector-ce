// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.config;

import com.zextras.carbonio.user_management.sdk.rest.model.MyselfDto;
import com.zextras.carbonio.user_management.sdk.rest.model.UserInfoDto;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registers, for the GraalVM native image, the carbonio-user-management-rest-sdk model classes that
 * get (de)serialized at runtime.
 *
 * <p>The SDK's {@code ApiClient} is built by hand in {@link
 * com.zextras.carbonio.docs_connector.producers.UserManagementClientProducer} via {@code
 * ApiClient.createDefaultObjectMapper()} and never goes through a RESTEasy Reactive JAX-RS resource
 * method, so nothing in RESTEasy Reactive's build-time type inference sees these DTOs. Quarkus's
 * {@code JacksonProcessor} only auto-registers a type when it carries one of a fixed set of Jackson
 * annotations ({@code @JsonDeserialize}, {@code @JsonSerialize}, {@code @JsonNaming},
 * {@code @JsonAutoDetect}, {@code @JsonCreator}, {@code @JsonSubTypes},
 * {@code @JsonTypeIdResolver}, {@code @JsonIdentityInfo}); the generated DTOs below carry only
 * {@code @JsonProperty}/{@code @JsonPropertyOrder}/{@code @JsonInclude}, none of which trigger that
 * inference. Without this explicit registration, native Jackson deserialization fails with {@code
 * InvalidDefinitionException}, which {@code
 * com.zextras.carbonio.user_management.sdk.rest.api.UserResourceApi} surfaces as an {@code
 * ApiException} with {@code getCode() == 0} (no HTTP status was ever parsed) — {@link
 * com.zextras.carbonio.docs_connector.auth.CookieAuthenticationFilter} then treats it like an
 * invalid cookie, so every {@code /files/*} request comes back 401 in native mode.
 *
 * <p>This lists every model class shipped by the resolved {@code carbonio-user-management-rest-sdk}
 * jar except {@code com.zextras.carbonio.user_management.sdk.rest.model.AbstractOpenApiSchema}:
 * that is the openapi-generator scaffolding base class for {@code oneOf}/{@code anyOf} schemas. The
 * UM {@code /internal} spec declares only two schemas — {@link MyselfDto} and {@link UserInfoDto} —
 * and neither is a oneOf/anyOf, so {@code AbstractOpenApiSchema} is never instantiated and needs no
 * reflection metadata.
 */
@RegisterForReflection(targets = {MyselfDto.class, UserInfoDto.class})
public final class UserManagementJacksonReflectionConfig {
  private UserManagementJacksonReflectionConfig() {}
}
