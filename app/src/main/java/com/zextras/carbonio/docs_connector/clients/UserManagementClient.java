// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.clients;

import com.zextras.carbonio.user_management.sdk.grpc.GetUserMyselfRequest;
import com.zextras.carbonio.user_management.sdk.grpc.UserManagementServiceGrpc.UserManagementServiceBlockingStub;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI wrapper around the carbonio-user-management gRPC blocking stub.
 *
 * <p>The {@code @ApplicationScoped} proxy is created at build time, but the actual gRPC stub is
 * only initialized at runtime on first use — after Quarkus runtime recorders have set up the gRPC
 * client infrastructure. Channel lifecycle (connect, reconnect, shutdown) is managed by Quarkus.
 *
 * <p>Channel configuration is in {@code application.properties} via:
 * <pre>
 *   quarkus.grpc.clients.user-management.host=${networking-config.carbonio.user-management.host}
 *   quarkus.grpc.clients.user-management.port=${networking-config.carbonio.user-management.port}
 * </pre>
 *
 * <p>Note: {@code @GrpcClient} field injection is the ONE exception to the constructor injection
 * rule — it is required by the Quarkus gRPC extension's build-time stub injection mechanism.
 */
@ApplicationScoped
public class UserManagementClient {

  private static final Logger logger = LoggerFactory.getLogger(UserManagementClient.class);

  @GrpcClient("user-management")
  UserManagementServiceBlockingStub blockingStub;

  public UserManagementServiceBlockingStub getBlockingStub() {
    return blockingStub;
  }

  /**
   * Probes the user-management gRPC service by issuing a lightweight RPC. Any response (including
   * UNAUTHENTICATED) means the service is reachable; only UNAVAILABLE or transport errors indicate
   * it is down.
   */
  public boolean isAlive() {
    try {
      blockingStub.getUserMyself(GetUserMyselfRequest.newBuilder().setToken("").build());
      return true;
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
        logger.debug("User-management gRPC probe: UNAVAILABLE");
        return false;
      }
      // Any other status (UNAUTHENTICATED, PERMISSION_DENIED, etc.) means service is alive
      return true;
    } catch (Exception e) {
      logger.debug("User-management gRPC probe failed: {}", e.getMessage());
      return false;
    }
  }
}
