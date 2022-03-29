package com.zextras.carbonio.docs_connector.entities.files.graphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

public class NodeAttributes {

  private String         id;
  private String         name;
  private String         extension;
  private Long           updated_at;
  private String         mime_type;
  private Integer        version;
  private Long           size;
  private User           owner;
  private Permissions    permissions;
  private NodeAttributes parent;

  public NodeAttributes() {}

  public static String getNodeGraphQLRequest(
    String nodeId,
    Optional<Integer> optVersion
  ) {

    String version = optVersion.map(v -> ", version: " + v).orElse("");
    return "{\"query\": "
      + "\"query {"
      + "    getNode(node_id: \\\"" + nodeId + "\\\"" + version + ") {"
      + "      permissions {"
      + "        can_write_file"
      + "      }"
      + "      owner {"
      + "        id"
      + "        full_name"
      + "      }"
      + "      parent {"
      + "        id"
      + "      }"
      + "      id"
      + "      name"
      + "      updated_at"
      + "      ... on File { "
      + "        extension"
      + "        mime_type"
      + "        size"
      + "        version"
      + "      }"
      + "    }"
      + "  }\""
      + "}";
  }

  public static NodeAttributes mapFromJSON(String graphQLResponse) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();

    String response = mapper
      .readTree(graphQLResponse)
      .get("data")
      .get("getNode")
      .toString();

    return mapper.readValue(response, NodeAttributes.class);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getExtension() {
    return extension;
  }

  public String getMime_type() {
    return mime_type;
  }

  public Long getUpdated_at() {
    return updated_at;
  }

  public Integer getVersion() {
    return version;
  }

  public Long getSize() {
    return size;
  }

  public User getOwner() {
    return owner;
  }

  public Permissions getPermissions() {
    return permissions;
  }

  public NodeAttributes getParent() {
    return parent;
  }
}
