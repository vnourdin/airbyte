/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.db.instance.configs.migrations;

import static io.airbyte.db.instance.configs.migrations.V0_32_8_001__AirbyteConfigDatabaseDenormalization.NamespaceDefinitionType.fromStandardSync;
import static io.airbyte.db.instance.configs.migrations.V0_32_8_001__AirbyteConfigDatabaseDenormalization.OperatorType.fromStandardSyncOperation;
import static org.jooq.impl.DSL.asterisk;
import static org.jooq.impl.DSL.foreignKey;
import static org.jooq.impl.DSL.primaryKey;

import com.google.common.annotations.VisibleForTesting;
import io.airbyte.commons.json.Jsons;
import io.airbyte.config.AirbyteConfig;
import io.airbyte.config.ConfigSchema;
import io.airbyte.config.ConfigWithMetadata;
import io.airbyte.config.DestinationConnection;
import io.airbyte.config.DestinationOAuthParameter;
import io.airbyte.config.JobSyncConfig;
import io.airbyte.config.SourceConnection;
import io.airbyte.config.SourceOAuthParameter;
import io.airbyte.config.StandardDestinationDefinition;
import io.airbyte.config.StandardSourceDefinition;
import io.airbyte.config.StandardSync;
import io.airbyte.config.StandardSync.Status;
import io.airbyte.config.StandardSyncOperation;
import io.airbyte.config.StandardSyncState;
import io.airbyte.config.StandardWorkspace;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.Catalog;
import org.jooq.DSLContext;
import org.jooq.EnumType;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.Schema;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.SchemaImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class V0_32_8_001__AirbyteConfigDatabaseDenormalization extends BaseJavaMigration {

  private static final Logger LOGGER = LoggerFactory.getLogger(V0_32_8_001__AirbyteConfigDatabaseDenormalization.class);

  @Override
  public void migrate(final Context context) throws Exception {

    // Warning: please do not use any jOOQ generated code to write a migration.
    // As database schema changes, the generated jOOQ code can be deprecated. So
    // old migration may not compile if there is any generated code.
    final DSLContext ctx = DSL.using(context.getConnection());
    migrate(ctx);
  }

  @VisibleForTesting
  public static void migrate(final DSLContext ctx) {
    createEnums(ctx);
    createAndPopulateWorkspace(ctx);
    createAndPopulateActorDefinition(ctx);
    createAndPopulateActor(ctx);
    crateAndPopulateActorOauthParameter(ctx);
    createAndPopulateOperation(ctx);
    createAndPopulateConnection(ctx);
    createAndPopulateState(ctx);
  }

  private static void createEnums(final DSLContext ctx) {
    ctx.createType("source_type").asEnum("api", "file", "database", "custom").execute();
    LOGGER.info("source_type enum created");
    ctx.createType("actor_type").asEnum("source", "destination").execute();
    LOGGER.info("actor_type enum created");
    ctx.createType("operator_type").asEnum("normalization", "dbt").execute();
    LOGGER.info("operator_type enum created");
    ctx.createType("namespace_definition_type").asEnum("source", "destination", "customformat").execute();
    LOGGER.info("namespace_definition_type enum created");
    ctx.createType("status_type").asEnum("active", "inactive", "deprecated").execute();
    LOGGER.info("status_type enum created");
  }

  private static void createAndPopulateWorkspace(final DSLContext ctx) {
    final Field<UUID> id = DSL.field("id", SQLDataType.UUID.nullable(false));
    final Field<String> name = DSL.field("name", SQLDataType.VARCHAR(256).nullable(false));
    final Field<String> slug = DSL.field("slug", SQLDataType.VARCHAR(256).nullable(false));
    final Field<Boolean> initialSetupComplete = DSL.field("initial_setup_complete", SQLDataType.BOOLEAN.nullable(false));
    final Field<UUID> customerId = DSL.field("customer_id", SQLDataType.UUID.nullable(true));
    final Field<String> email = DSL.field("email", SQLDataType.VARCHAR(256).nullable(true));
    final Field<Boolean> anonymousDataCollection = DSL.field("anonymous_data_collection", SQLDataType.BOOLEAN.nullable(true));
    final Field<Boolean> news = DSL.field("news", SQLDataType.BOOLEAN.nullable(true));
    final Field<Boolean> securityUpdates = DSL.field("security_updates", SQLDataType.BOOLEAN.nullable(true));
    final Field<Boolean> displaySetupWizard = DSL.field("display_setup_wizard", SQLDataType.BOOLEAN.nullable(true));
    final Field<Boolean> tombstone = DSL.field("tombstone", SQLDataType.BOOLEAN.nullable(true));
    final Field<JSONB> notifications = DSL.field("notifications", SQLDataType.JSONB.nullable(true));
    final Field<Boolean> firstCompletedSync = DSL.field("first_completed_sync", SQLDataType.BOOLEAN.nullable(true));
    final Field<Boolean> feedbackDone = DSL.field("feedback_done", SQLDataType.BOOLEAN.nullable(true));
    final Field<OffsetDateTime> createdAt = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));
    final Field<OffsetDateTime> updatedAt = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));

    ctx.createTableIfNotExists("workspace")
        .columns(id,
            customerId,
            name,
            slug,
            email,
            initialSetupComplete,
            anonymousDataCollection,
            news,
            securityUpdates,
            displaySetupWizard,
            tombstone,
            notifications,
            firstCompletedSync,
            feedbackDone,
            createdAt,
            updatedAt)
        .constraints(primaryKey(id))
        .execute();
    LOGGER.info("workspace table created");
    final List<ConfigWithMetadata<StandardWorkspace>> configsWithMetadata = listConfigsWithMetadata(ConfigSchema.STANDARD_WORKSPACE,
        StandardWorkspace.class,
        ctx);

    for (final ConfigWithMetadata<StandardWorkspace> configWithMetadata : configsWithMetadata) {
      final StandardWorkspace standardWorkspace = configWithMetadata.getConfig();
      ctx.insertInto(DSL.table("workspace"))
          .set(id, standardWorkspace.getWorkspaceId())
          .set(customerId, standardWorkspace.getCustomerId())
          .set(name, standardWorkspace.getName())
          .set(slug, standardWorkspace.getSlug())
          .set(email, standardWorkspace.getEmail())
          .set(initialSetupComplete, standardWorkspace.getInitialSetupComplete())
          .set(anonymousDataCollection, standardWorkspace.getAnonymousDataCollection())
          .set(news, standardWorkspace.getNews())
          .set(securityUpdates, standardWorkspace.getSecurityUpdates())
          .set(displaySetupWizard, standardWorkspace.getDisplaySetupWizard())
          .set(tombstone, standardWorkspace.getTombstone())
          .set(notifications, JSONB.valueOf(Jsons.serialize(standardWorkspace.getNotifications())))
          .set(firstCompletedSync, standardWorkspace.getFirstCompletedSync())
          .set(feedbackDone, standardWorkspace.getFeedbackDone())
          .set(createdAt, OffsetDateTime.ofInstant(configWithMetadata.getCreatedAt(), ZoneOffset.UTC))
          .set(updatedAt, OffsetDateTime.ofInstant(configWithMetadata.getUpdatedAt(), ZoneOffset.UTC))
          .execute();
    }
    LOGGER.info("workspace table populated with " + configsWithMetadata.size() + " records");
  }

  private static void createAndPopulateActorDefinition(DSLContext ctx) {
    final Field<UUID> id = DSL.field("id", SQLDataType.UUID.nullable(false));
    final Field<String> name = DSL.field("name", SQLDataType.VARCHAR(256).nullable(false));
    final Field<String> dockerRepository = DSL.field("docker_repository", SQLDataType.VARCHAR(256).nullable(false));
    final Field<String> dockerImageTag = DSL.field("docker_image_tag", SQLDataType.VARCHAR(256).nullable(false));
    final Field<String> documentationUrl = DSL.field("documentation_url", SQLDataType.VARCHAR(256).nullable(false));
    final Field<JSONB> spec = DSL.field("spec", SQLDataType.JSONB.nullable(false));
    final Field<String> icon = DSL.field("icon", SQLDataType.VARCHAR(256).nullable(true));
    final Field<ActorType> actorType = DSL.field("actor_type", SQLDataType.VARCHAR.asEnumDataType(ActorType.class).nullable(false));
    final Field<SourceType> sourceType = DSL.field("source_type", SQLDataType.VARCHAR.asEnumDataType(SourceType.class).nullable(true));
    final Field<OffsetDateTime> createdAt = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));
    final Field<OffsetDateTime> updatedAt = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));

    ctx.createTableIfNotExists("actor_definition")
        .columns(id,
            name,
            dockerRepository,
            dockerImageTag,
            documentationUrl,
            icon,
            actorType,
            sourceType,
            spec,
            createdAt,
            updatedAt)
        .constraints(primaryKey(id))
        .execute();
    ctx.createIndex("actor_definition_actor_type_idx").on("actor_definition", "actor_type").execute();

    LOGGER.info("actor_definition table created");

    final List<ConfigWithMetadata<StandardSourceDefinition>> sourceDefinitionsWithMetadata = listConfigsWithMetadata(
        ConfigSchema.STANDARD_SOURCE_DEFINITION,
        StandardSourceDefinition.class,
        ctx);

    for (final ConfigWithMetadata<StandardSourceDefinition> configWithMetadata : sourceDefinitionsWithMetadata) {
      final StandardSourceDefinition standardSourceDefinition = configWithMetadata.getConfig();
      ctx.insertInto(DSL.table("actor_definition"))
          .set(id, standardSourceDefinition.getSourceDefinitionId())
          .set(name, standardSourceDefinition.getName())
          .set(dockerRepository, standardSourceDefinition.getDockerRepository())
          .set(dockerImageTag, standardSourceDefinition.getDockerImageTag())
          .set(documentationUrl, standardSourceDefinition.getDocumentationUrl())
          .set(icon, standardSourceDefinition.getIcon())
          .set(actorType, ActorType.source)
          .set(sourceType, SourceType.fromStandardSourceDefinition(standardSourceDefinition.getSourceType()))
          .set(spec, JSONB.valueOf(Jsons.serialize(standardSourceDefinition.getSpec())))
          .set(createdAt, OffsetDateTime.ofInstant(configWithMetadata.getCreatedAt(), ZoneOffset.UTC))
          .set(updatedAt, OffsetDateTime.ofInstant(configWithMetadata.getUpdatedAt(), ZoneOffset.UTC))
          .execute();
    }
    LOGGER.info("actor_definition table populated with " + sourceDefinitionsWithMetadata.size() + " source definition records");

    final List<ConfigWithMetadata<StandardDestinationDefinition>> destinationDefinitionsWithMetadata = listConfigsWithMetadata(
        ConfigSchema.STANDARD_DESTINATION_DEFINITION,
        StandardDestinationDefinition.class,
        ctx);

    for (final ConfigWithMetadata<StandardDestinationDefinition> configWithMetadata : destinationDefinitionsWithMetadata) {
      final StandardDestinationDefinition standardDestinationDefinition = configWithMetadata.getConfig();
      ctx.insertInto(DSL.table("actor_definition"))
          .set(id, standardDestinationDefinition.getDestinationDefinitionId())
          .set(name, standardDestinationDefinition.getName())
          .set(dockerRepository, standardDestinationDefinition.getDockerRepository())
          .set(dockerImageTag, standardDestinationDefinition.getDockerImageTag())
          .set(documentationUrl, standardDestinationDefinition.getDocumentationUrl())
          .set(icon, standardDestinationDefinition.getIcon())
          .set(actorType, ActorType.destination)
          .set(spec, JSONB.valueOf(Jsons.serialize(standardDestinationDefinition.getSpec())))
          .set(createdAt, OffsetDateTime.ofInstant(configWithMetadata.getCreatedAt(), ZoneOffset.UTC))
          .set(updatedAt, OffsetDateTime.ofInstant(configWithMetadata.getUpdatedAt(), ZoneOffset.UTC))
          .execute();
    }
    LOGGER.info("actor_definition table populated with " + destinationDefinitionsWithMetadata.size() + " destination definition records");
  }

  private static void createAndPopulateActor(DSLContext ctx) {
    final Field<UUID> id = DSL.field("id", SQLDataType.UUID.nullable(false));
    final Field<String> name = DSL.field("name", SQLDataType.VARCHAR(256).nullable(false));
    final Field<UUID> actorDefinitionId = DSL.field("actor_definition_id", SQLDataType.UUID.nullable(false));
    final Field<UUID> workspaceId = DSL.field("workspace_id", SQLDataType.UUID.nullable(false));
    final Field<JSONB> configuration = DSL.field("configuration", SQLDataType.JSONB.nullable(false));
    final Field<ActorType> actorType = DSL.field("actor_type", SQLDataType.VARCHAR.asEnumDataType(ActorType.class).nullable(false));
    final Field<Boolean> tombstone = DSL.field("tombstone", SQLDataType.BOOLEAN.nullable(false));
    final Field<OffsetDateTime> createdAt = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));
    final Field<OffsetDateTime> updatedAt = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));

    ctx.createTableIfNotExists("actor")
        .columns(id,
            workspaceId,
            actorDefinitionId,
            name,
            configuration,
            actorType,
            tombstone,
            createdAt,
            updatedAt)
        .constraints(primaryKey(id),
            foreignKey(workspaceId).references("workspace", "id"),
            foreignKey(actorDefinitionId).references("actor_definition", "id"))
        .execute();
    ctx.createIndex("actor_actor_type_idx").on("actor", "actor_type").execute();
    ctx.createIndex("actor_actor_definition_id_idx").on("actor", "actor_definition_id").execute();

    LOGGER.info("actor table created");

    final List<ConfigWithMetadata<SourceConnection>> sourcesWithMetadata = listConfigsWithMetadata(
        ConfigSchema.SOURCE_CONNECTION,
        SourceConnection.class,
        ctx);

    for (final ConfigWithMetadata<SourceConnection> configWithMetadata : sourcesWithMetadata) {
      final SourceConnection sourceConnection = configWithMetadata.getConfig();
      ctx.insertInto(DSL.table("actor"))
          .set(id, sourceConnection.getSourceId())
          .set(workspaceId, sourceConnection.getWorkspaceId())
          .set(actorDefinitionId, sourceConnection.getSourceDefinitionId())
          .set(name, sourceConnection.getName())
          .set(configuration, JSONB.valueOf(Jsons.serialize(sourceConnection.getConfiguration())))
          .set(actorType, ActorType.source)
          .set(tombstone, sourceConnection.getTombstone())
          .set(createdAt, OffsetDateTime.ofInstant(configWithMetadata.getCreatedAt(), ZoneOffset.UTC))
          .set(updatedAt, OffsetDateTime.ofInstant(configWithMetadata.getUpdatedAt(), ZoneOffset.UTC))
          .execute();
    }
    LOGGER.info("actor table populated with " + sourcesWithMetadata.size() + " source records");

    final List<ConfigWithMetadata<DestinationConnection>> destinationsWithMetadata = listConfigsWithMetadata(
        ConfigSchema.DESTINATION_CONNECTION,
        DestinationConnection.class,
        ctx);

    for (final ConfigWithMetadata<DestinationConnection> configWithMetadata : destinationsWithMetadata) {
      final DestinationConnection destinationConnection = configWithMetadata.getConfig();
      ctx.insertInto(DSL.table("actor"))
          .set(id, destinationConnection.getDestinationId())
          .set(workspaceId, destinationConnection.getWorkspaceId())
          .set(actorDefinitionId, destinationConnection.getDestinationDefinitionId())
          .set(name, destinationConnection.getName())
          .set(configuration, JSONB.valueOf(Jsons.serialize(destinationConnection.getConfiguration())))
          .set(actorType, ActorType.destination)
          .set(tombstone, destinationConnection.getTombstone())
          .set(createdAt, OffsetDateTime.ofInstant(configWithMetadata.getCreatedAt(), ZoneOffset.UTC))
          .set(updatedAt, OffsetDateTime.ofInstant(configWithMetadata.getUpdatedAt(), ZoneOffset.UTC))
          .execute();
    }
    LOGGER.info("actor table populated with " + destinationsWithMetadata.size() + " destination records");
  }

  private static void crateAndPopulateActorOauthParameter(final DSLContext ctx) {
    final Field<UUID> id = DSL.field("id", SQLDataType.UUID.nullable(false));
    final Field<UUID> actorDefinitionId = DSL.field("actor_definition_id", SQLDataType.UUID.nullable(false));
    final Field<JSONB> configuration = DSL.field("configuration", SQLDataType.JSONB.nullable(false));
    final Field<UUID> workspaceId = DSL.field("workspace_id", SQLDataType.UUID.nullable(true));
    final Field<ActorType> actorType = DSL.field("actor_type", SQLDataType.VARCHAR.asEnumDataType(ActorType.class).nullable(false));
    final Field<OffsetDateTime> createdAt = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));
    final Field<OffsetDateTime> updatedAt = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));

    ctx.createTableIfNotExists("actor_oauth_parameter")
        .columns(id,
            workspaceId,
            actorDefinitionId,
            configuration,
            actorType,
            createdAt,
            updatedAt)
        .constraints(primaryKey(id),
            foreignKey(workspaceId).references("workspace", "id"),
            foreignKey(actorDefinitionId).references("actor_definition", "id"))
        .execute();
    ctx.createIndex("actor_oauth_parameter_actor_type_idx").on("actor_oauth_parameter", "actor_type").execute();

    LOGGER.info("actor_oauth_parameter table created");

    final List<ConfigWithMetadata<SourceOAuthParameter>> sourceOauthParamsWithMetadata = listConfigsWithMetadata(
        ConfigSchema.SOURCE_OAUTH_PARAM,
        SourceOAuthParameter.class,
        ctx);

    for (final ConfigWithMetadata<SourceOAuthParameter> configWithMetadata : sourceOauthParamsWithMetadata) {
      final SourceOAuthParameter sourceOAuthParameter = configWithMetadata.getConfig();
      ctx.insertInto(DSL.table("actor_oauth_parameter"))
          .set(id, sourceOAuthParameter.getOauthParameterId())
          .set(workspaceId, sourceOAuthParameter.getWorkspaceId())
          .set(actorDefinitionId, sourceOAuthParameter.getSourceDefinitionId())
          .set(configuration, JSONB.valueOf(Jsons.serialize(sourceOAuthParameter.getConfiguration())))
          .set(actorType, ActorType.source)
          .set(createdAt, OffsetDateTime.ofInstant(configWithMetadata.getCreatedAt(), ZoneOffset.UTC))
          .set(updatedAt, OffsetDateTime.ofInstant(configWithMetadata.getUpdatedAt(), ZoneOffset.UTC))
          .execute();
    }

    LOGGER.info("actor_oauth_parameter table populated with " + sourceOauthParamsWithMetadata.size() + " source oauth params records");

    final List<ConfigWithMetadata<DestinationOAuthParameter>> destinationOauthParamsWithMetadata = listConfigsWithMetadata(
        ConfigSchema.DESTINATION_OAUTH_PARAM,
        DestinationOAuthParameter.class,
        ctx);

    for (final ConfigWithMetadata<DestinationOAuthParameter> configWithMetadata : destinationOauthParamsWithMetadata) {
      final DestinationOAuthParameter destinationOAuthParameter = configWithMetadata.getConfig();
      ctx.insertInto(DSL.table("actor_oauth_parameter"))
          .set(id, destinationOAuthParameter.getOauthParameterId())
          .set(workspaceId, destinationOAuthParameter.getWorkspaceId())
          .set(actorDefinitionId, destinationOAuthParameter.getDestinationDefinitionId())
          .set(configuration, JSONB.valueOf(Jsons.serialize(destinationOAuthParameter.getConfiguration())))
          .set(actorType, ActorType.destination)
          .set(createdAt, OffsetDateTime.ofInstant(configWithMetadata.getCreatedAt(), ZoneOffset.UTC))
          .set(updatedAt, OffsetDateTime.ofInstant(configWithMetadata.getUpdatedAt(), ZoneOffset.UTC))
          .execute();
    }

    LOGGER.info("actor_oauth_parameter table populated with " + destinationOauthParamsWithMetadata.size() + " destination oauth params records");
  }

  private static void createAndPopulateOperation(final DSLContext ctx) {
    final Field<UUID> id = DSL.field("id", SQLDataType.UUID.nullable(false));
    final Field<UUID> workspaceId = DSL.field("workspace_id", SQLDataType.UUID.nullable(false));
    final Field<String> name = DSL.field("name", SQLDataType.VARCHAR(256).nullable(false));
    final Field<OperatorType> operatorType = DSL.field("operator_type", SQLDataType.VARCHAR.asEnumDataType(OperatorType.class).nullable(false));
    final Field<JSONB> operatorNormalization = DSL.field("operator_normalization", SQLDataType.JSONB.nullable(true));
    final Field<JSONB> operatorDbt = DSL.field("operator_dbt", SQLDataType.JSONB.nullable(true));
    final Field<Boolean> tombstone = DSL.field("tombstone", SQLDataType.BOOLEAN.nullable(true));
    final Field<OffsetDateTime> createdAt = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));
    final Field<OffsetDateTime> updatedAt = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));

    ctx.createTableIfNotExists("operation")
        .columns(id,
            workspaceId,
            name,
            operatorType,
            operatorNormalization,
            operatorDbt,
            tombstone,
            createdAt,
            updatedAt)
        .constraints(primaryKey(id),
            foreignKey(workspaceId).references("workspace", "id"))
        .execute();

    LOGGER.info("operation table created");

    final List<ConfigWithMetadata<StandardSyncOperation>> configsWithMetadata = listConfigsWithMetadata(
        ConfigSchema.STANDARD_SYNC_OPERATION,
        StandardSyncOperation.class,
        ctx);

    for (final ConfigWithMetadata<StandardSyncOperation> configWithMetadata : configsWithMetadata) {
      final StandardSyncOperation standardSyncOperation = configWithMetadata.getConfig();
      ctx.insertInto(DSL.table("operation"))
          .set(id, standardSyncOperation.getOperationId())
          .set(workspaceId, standardSyncOperation.getWorkspaceId())
          .set(name, standardSyncOperation.getName())
          .set(operatorType, fromStandardSyncOperation(standardSyncOperation.getOperatorType()))
          .set(operatorNormalization, JSONB.valueOf(Jsons.serialize(standardSyncOperation.getOperatorNormalization())))
          .set(operatorDbt, JSONB.valueOf(Jsons.serialize(standardSyncOperation.getOperatorDbt())))
          .set(tombstone, standardSyncOperation.getTombstone())
          .set(createdAt, OffsetDateTime.ofInstant(configWithMetadata.getCreatedAt(), ZoneOffset.UTC))
          .set(updatedAt, OffsetDateTime.ofInstant(configWithMetadata.getUpdatedAt(), ZoneOffset.UTC))
          .execute();
    }

    LOGGER.info("operation table populated with " + configsWithMetadata.size() + " records");
  }

  private static void createAndPopulateConnection(final DSLContext ctx) {
    final Field<UUID> id = DSL.field("id", SQLDataType.UUID.nullable(false));
    final Field<NamespaceDefinitionType> namespaceDefinition = DSL
        .field("namespace_definition", SQLDataType.VARCHAR.asEnumDataType(NamespaceDefinitionType.class).nullable(false));
    final Field<String> namespaceFormat = DSL.field("namespace_format", SQLDataType.VARCHAR(256).nullable(true));
    final Field<String> prefix = DSL.field("prefix", SQLDataType.VARCHAR(256).nullable(true));
    final Field<UUID> sourceId = DSL.field("source_id", SQLDataType.UUID.nullable(false));
    final Field<UUID> destinationId = DSL.field("destination_id", SQLDataType.UUID.nullable(false));
    final Field<String> name = DSL.field("name", SQLDataType.VARCHAR(256).nullable(false));
    final Field<JSONB> catalog = DSL.field("catalog", SQLDataType.JSONB.nullable(false));
    final Field<StatusType> status = DSL.field("status", SQLDataType.VARCHAR.asEnumDataType(StatusType.class).nullable(true));
    final Field<JSONB> schedule = DSL.field("schedule", SQLDataType.JSONB.nullable(true));
    final Field<Boolean> manual = DSL.field("manual", SQLDataType.BOOLEAN.nullable(false));
    final Field<JSONB> resourceRequirements = DSL.field("resource_requirements", SQLDataType.JSONB.nullable(true));
    final Field<OffsetDateTime> createdAt = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));
    final Field<OffsetDateTime> updatedAt = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));

    ctx.createTableIfNotExists("connection")
        .columns(id,
            namespaceDefinition,
            namespaceFormat,
            prefix,
            sourceId,
            destinationId,
            name,
            catalog,
            status,
            schedule,
            manual,
            resourceRequirements,
            createdAt,
            updatedAt)
        .constraints(primaryKey(id),
            foreignKey(sourceId).references("actor", "id"),
            foreignKey(destinationId).references("actor", "id"))
        .execute();
    ctx.createIndex("connection_source_id_idx").on("connection", "source_id").execute();
    ctx.createIndex("connection_destination_id_idx").on("connection", "destination_id").execute();

    LOGGER.info("connection table created");

    final List<ConfigWithMetadata<StandardSync>> configsWithMetadata = listConfigsWithMetadata(
        ConfigSchema.STANDARD_SYNC,
        StandardSync.class,
        ctx);
    boolean connectionOperationCreated = false;
    for (final ConfigWithMetadata<StandardSync> configWithMetadata : configsWithMetadata) {
      final StandardSync standardSync = configWithMetadata.getConfig();
      ctx.insertInto(DSL.table("connection"))
          .set(id, standardSync.getConnectionId())
          .set(namespaceDefinition, fromStandardSync(standardSync.getNamespaceDefinition()))
          .set(namespaceFormat, standardSync.getNamespaceFormat())
          .set(prefix, standardSync.getPrefix())
          .set(sourceId, standardSync.getSourceId())
          .set(destinationId, standardSync.getDestinationId())
          .set(name, standardSync.getName())
          .set(catalog, JSONB.valueOf(Jsons.serialize(standardSync.getCatalog())))
          .set(status, StatusType.fromStandardSync(standardSync.getStatus()))
          .set(schedule, JSONB.valueOf(Jsons.serialize(standardSync.getSchedule())))
          .set(manual, standardSync.getManual())
          .set(resourceRequirements, JSONB.valueOf(Jsons.serialize(standardSync.getResourceRequirements())))
          .set(createdAt, OffsetDateTime.ofInstant(configWithMetadata.getCreatedAt(), ZoneOffset.UTC))
          .set(updatedAt, OffsetDateTime.ofInstant(configWithMetadata.getUpdatedAt(), ZoneOffset.UTC))
          .execute();
      createAndPopulateConnectionOperation(ctx, !connectionOperationCreated, configWithMetadata);
      connectionOperationCreated = true;
    }

    LOGGER.info("connection table populated with " + configsWithMetadata.size() + " records");
  }

  private static void createAndPopulateState(final DSLContext ctx) {
    final Field<UUID> id = DSL.field("id", SQLDataType.UUID.nullable(false));
    final Field<UUID> connectionId = DSL.field("connection_id", SQLDataType.UUID.nullable(false));
    final Field<JSONB> state = DSL.field("state", SQLDataType.JSONB.nullable(true));
    final Field<OffsetDateTime> createdAt = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));
    final Field<OffsetDateTime> updatedAt = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));

    ctx.createTableIfNotExists("state")
        .columns(id,
            connectionId,
            state,
            createdAt,
            updatedAt)
        .constraints(primaryKey(id, connectionId),
            foreignKey(connectionId).references("connection", "id"))
        .execute();

    LOGGER.info("state table created");

    final List<ConfigWithMetadata<StandardSyncState>> configsWithMetadata = listConfigsWithMetadata(
        ConfigSchema.STANDARD_SYNC_STATE,
        StandardSyncState.class,
        ctx);

    for (final ConfigWithMetadata<StandardSyncState> configWithMetadata : configsWithMetadata) {
      final StandardSyncState standardSyncState = configWithMetadata.getConfig();
      ctx.insertInto(DSL.table("state"))
          .set(id, UUID.randomUUID())
          .set(connectionId, standardSyncState.getConnectionId())
          .set(state, JSONB.valueOf(Jsons.serialize(standardSyncState.getState())))
          .set(createdAt, OffsetDateTime.ofInstant(configWithMetadata.getCreatedAt(), ZoneOffset.UTC))
          .set(updatedAt, OffsetDateTime.ofInstant(configWithMetadata.getUpdatedAt(), ZoneOffset.UTC))
          .execute();
    }

    LOGGER.info("state table populated with " + configsWithMetadata.size() + " records");
  }

  private static void createAndPopulateConnectionOperation(final DSLContext ctx,
                                                           final boolean createTable,
                                                           final ConfigWithMetadata<StandardSync> standardSyncWithMetadata) {
    final Field<UUID> id = DSL.field("id", SQLDataType.UUID.nullable(false));
    final Field<UUID> connectionId = DSL.field("connection_id", SQLDataType.UUID.nullable(false));
    final Field<UUID> operationId = DSL.field("operation_id", SQLDataType.UUID.nullable(false));
    final Field<OffsetDateTime> createdAt = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));
    final Field<OffsetDateTime> updatedAt = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));

    if (createTable) {
      ctx.createTableIfNotExists("connection_operation")
          .columns(id,
              connectionId,
              operationId,
              createdAt,
              updatedAt)
          .constraints(primaryKey(id, connectionId, operationId),
              foreignKey(connectionId).references("connection", "id"),
              foreignKey(operationId).references("operation", "id"))
          .execute();
      LOGGER.info("connection_operation table created");
    }
    final StandardSync standardSync = standardSyncWithMetadata.getConfig();
    for (final UUID operationIdFromStandardSync : standardSync.getOperationIds()) {
      ctx.insertInto(DSL.table("connection_operation"))
          .set(id, UUID.randomUUID())
          .set(connectionId, standardSync.getConnectionId())
          .set(operationId, operationIdFromStandardSync)
          .set(createdAt, OffsetDateTime.ofInstant(standardSyncWithMetadata.getCreatedAt(), ZoneOffset.UTC))
          .set(updatedAt, OffsetDateTime.ofInstant(standardSyncWithMetadata.getUpdatedAt(), ZoneOffset.UTC))
          .execute();
    }
    LOGGER.info("connection_operation table populated with " + standardSync.getOperationIds().size() + " records");
  }

  private static <T> List<ConfigWithMetadata<T>> listConfigsWithMetadata(final AirbyteConfig airbyteConfigType,
                                                                         final Class<T> clazz,
                                                                         final DSLContext ctx) {
    final Field<String> configId = DSL.field("config_id", SQLDataType.VARCHAR(36).nullable(false));
    final Field<String> configType = DSL.field("config_type", SQLDataType.VARCHAR(60).nullable(false));
    final Field<OffsetDateTime> createdAt = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));
    final Field<OffsetDateTime> updatedAt = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false));
    final Field<JSONB> configBlob = DSL.field("config_blob", SQLDataType.JSONB.nullable(false));
    final Result<Record> results = ctx.select(asterisk()).from(DSL.table("airbyte_configs")).where(configType.eq(airbyteConfigType.name())).fetch();

    return results.stream().map(record -> new ConfigWithMetadata<>(
        record.get(configId),
        record.get(configType),
        record.get(createdAt).toInstant(),
        record.get(updatedAt).toInstant(),
        Jsons.deserialize(record.get(configBlob).data(), clazz)))
        .collect(Collectors.toList());
  }

  public enum SourceType implements EnumType {

    api("api"),
    file("file"),
    database("database"),
    custom("custom");

    private final String literal;

    SourceType(String literal) {
      this.literal = literal;
    }

    @Override
    public Catalog getCatalog() {
      return getSchema() == null ? null : getSchema().getCatalog();
    }

    @Override
    public Schema getSchema() {
      return new SchemaImpl(DSL.name("public"), null);

    }

    @Override
    public String getName() {
      return "source_type";
    }

    @Override
    public String getLiteral() {
      return literal;
    }

    public static SourceType fromStandardSourceDefinition(StandardSourceDefinition.SourceType sourceType) {
      switch (sourceType) {
        case API -> {
          return api;
        }
        case FILE -> {
          return file;
        }
        case DATABASE -> {
          return database;
        }
        case CUSTOM -> {
          return custom;
        }
      }
      throw new IllegalArgumentException("Unidentified source type " + sourceType);
    }

    public static StandardSourceDefinition.SourceType fromSourceType(SourceType sourceType) {
      switch (sourceType) {

        case api -> {
          return StandardSourceDefinition.SourceType.API;
        }
        case file -> {
          return StandardSourceDefinition.SourceType.FILE;
        }
        case database -> {
          return StandardSourceDefinition.SourceType.DATABASE;
        }
        case custom -> {
          return StandardSourceDefinition.SourceType.CUSTOM;
        }
      }
      throw new IllegalArgumentException("Unidentified source type " + sourceType);
    }

  }

  public enum NamespaceDefinitionType implements EnumType {

    source("source"),
    destination("destination"),
    customformat("customformat");

    private final String literal;

    NamespaceDefinitionType(String literal) {
      this.literal = literal;
    }

    @Override
    public Catalog getCatalog() {
      return getSchema() == null ? null : getSchema().getCatalog();
    }

    @Override
    public Schema getSchema() {
      return new SchemaImpl(DSL.name("public"), null);

    }

    @Override
    public String getName() {
      return "namespace_definition_type";
    }

    @Override
    public String getLiteral() {
      return literal;
    }

    public static NamespaceDefinitionType fromStandardSync(io.airbyte.config.JobSyncConfig.NamespaceDefinitionType namespaceDefinitionType) {
      switch (namespaceDefinitionType) {

        case SOURCE -> {
          return source;
        }
        case DESTINATION -> {
          return destination;
        }
        case CUSTOMFORMAT -> {
          return customformat;
        }
      }
      throw new IllegalArgumentException("Unidentified namespace definition type " + namespaceDefinitionType);
    }

    public static io.airbyte.config.JobSyncConfig.NamespaceDefinitionType fromNamespaceDefinitionType(
                                                                                                      NamespaceDefinitionType namespaceDefinitionType) {
      switch (namespaceDefinitionType) {

        case source -> {
          return JobSyncConfig.NamespaceDefinitionType.SOURCE;
        }
        case destination -> {
          return JobSyncConfig.NamespaceDefinitionType.DESTINATION;
        }
        case customformat -> {
          return JobSyncConfig.NamespaceDefinitionType.CUSTOMFORMAT;
        }
      }
      throw new IllegalArgumentException("Unidentified namespace definition type " + namespaceDefinitionType);
    }

  }

  public enum StatusType implements EnumType {

    active("active"),
    inactive("inactive"),
    deprecated("deprecated");

    private final String literal;

    StatusType(String literal) {
      this.literal = literal;
    }

    @Override
    public Catalog getCatalog() {
      return getSchema() == null ? null : getSchema().getCatalog();
    }

    @Override
    public Schema getSchema() {
      return new SchemaImpl(DSL.name("public"), null);

    }

    @Override
    public String getName() {
      return "status_type";
    }

    @Override
    public String getLiteral() {
      return literal;
    }

    public static StatusType fromStandardSync(StandardSync.Status status) {
      switch (status) {
        case ACTIVE -> {
          return active;
        }
        case INACTIVE -> {
          return inactive;
        }
        case DEPRECATED -> {
          return deprecated;
        }
      }
      throw new IllegalArgumentException("Unidentified status type " + status);
    }

    public static StandardSync.Status fromStatusType(StatusType status) {
      switch (status) {

        case active -> {
          return Status.ACTIVE;
        }
        case inactive -> {
          return Status.INACTIVE;
        }
        case deprecated -> {
          return Status.DEPRECATED;
        }
      }
      throw new IllegalArgumentException("Unidentified status type " + status);
    }

  }

  public enum OperatorType implements EnumType {

    normalization("normalization"),
    dbt("dbt");

    private final String literal;

    OperatorType(String literal) {
      this.literal = literal;
    }

    @Override
    public Catalog getCatalog() {
      return getSchema() == null ? null : getSchema().getCatalog();
    }

    @Override
    public Schema getSchema() {
      return new SchemaImpl(DSL.name("public"), null);
    }

    @Override
    public String getName() {
      return "operator_type";
    }

    @Override
    public String getLiteral() {
      return literal;
    }

    public static OperatorType fromStandardSyncOperation(StandardSyncOperation.OperatorType operatorType) {
      switch (operatorType) {

        case NORMALIZATION -> {
          return normalization;
        }
        case DBT -> {
          return dbt;
        }
      }
      throw new IllegalArgumentException("Unidentified operator type " + operatorType);
    }

    public static StandardSyncOperation.OperatorType fromOperatorType(OperatorType operatorType) {
      switch (operatorType) {
        case normalization -> {
          return StandardSyncOperation.OperatorType.NORMALIZATION;
        }
        case dbt -> {
          return StandardSyncOperation.OperatorType.DBT;
        }
      }
      throw new IllegalArgumentException("Unidentified operator type " + operatorType);
    }

  }

  public enum ActorType implements EnumType {

    source("source"),
    destination("destination");

    private final String literal;

    ActorType(String literal) {
      this.literal = literal;
    }

    @Override
    public Catalog getCatalog() {
      return getSchema() == null ? null : getSchema().getCatalog();
    }

    @Override
    public Schema getSchema() {
      return new SchemaImpl(DSL.name("public"), null);
    }

    @Override
    public String getName() {
      return "actor_type";
    }

    @Override
    public String getLiteral() {
      return literal;
    }

  }

}
