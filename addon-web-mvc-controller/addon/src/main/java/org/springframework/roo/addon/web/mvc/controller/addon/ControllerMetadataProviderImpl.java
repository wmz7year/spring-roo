package org.springframework.roo.addon.web.mvc.controller.addon;

import static org.springframework.roo.model.RooJavaType.ROO_CONTROLLER;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.addon.jpa.addon.entity.JpaEntityMetadata;
import org.springframework.roo.addon.jpa.addon.entity.JpaEntityMetadata.RelationInfo;
import org.springframework.roo.addon.layers.service.addon.ServiceLocator;
import org.springframework.roo.addon.layers.service.addon.ServiceMetadata;
import org.springframework.roo.addon.plural.addon.PluralService;
import org.springframework.roo.addon.web.mvc.controller.annotations.ControllerType;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.customdata.taggers.CustomDataKeyDecorator;
import org.springframework.roo.classpath.customdata.taggers.CustomDataKeyDecoratorTracker;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.ItdTypeDetails;
import org.springframework.roo.classpath.details.MemberHoldingTypeDetails;
import org.springframework.roo.classpath.details.annotations.AnnotationAttributeValue;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.classpath.itd.AbstractMemberDiscoveringItdMetadataProvider;
import org.springframework.roo.classpath.itd.ItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.classpath.operations.Cardinality;
import org.springframework.roo.classpath.scanner.MemberDetails;
import org.springframework.roo.metadata.MetadataDependencyRegistry;
import org.springframework.roo.metadata.MetadataIdentificationUtils;
import org.springframework.roo.metadata.internal.MetadataDependencyRegistryTracker;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.model.JpaJavaType;
import org.springframework.roo.model.RooJavaType;
import org.springframework.roo.project.LogicalPath;
import org.springframework.roo.support.logging.HandlerUtils;
import org.springframework.roo.support.osgi.ServiceInstaceManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Implementation of {@link ControllerMetadataProvider}.
 *
 * @author Juan Carlos García
 * @since 2.0
 */
@Component
@Service
public class ControllerMetadataProviderImpl extends AbstractMemberDiscoveringItdMetadataProvider
    implements ControllerMetadataProvider {

  protected final static Logger LOGGER = HandlerUtils
      .getLogger(ControllerMetadataProviderImpl.class);

  private final Map<JavaType, String> domainTypeToServiceMidMap =
      new LinkedHashMap<JavaType, String>();

  protected MetadataDependencyRegistryTracker registryTracker = null;
  protected CustomDataKeyDecoratorTracker keyDecoratorTracker = null;

  private ServiceInstaceManager serviceInstaceManager = new ServiceInstaceManager();


  /**
   * This service is being activated so setup it:
   * <ul>
   * <li>Create and open the {@link MetadataDependencyRegistryTracker}.</li>
   * <li>Create and open the {@link CustomDataKeyDecoratorTracker}.</li>
   * <li>Registers {@link RooJavaType#ROO_CONTROLLER} as additional JavaType
   * that will trigger metadata registration.</li>
   * <li>Set ensure the governor type details represent a class.</li>
   * </ul>
   */
  @Override
  @SuppressWarnings("unchecked")
  protected void activate(final ComponentContext cContext) {
    context = cContext.getBundleContext();
    serviceInstaceManager.activate(this.context);
    super.setDependsOnGovernorBeingAClass(false);
    this.registryTracker =
        new MetadataDependencyRegistryTracker(context, this,
            PhysicalTypeIdentifier.getMetadataIdentiferType(), getProvidesType());
    this.registryTracker.open();

    addMetadataTrigger(ROO_CONTROLLER);
  }

  /**
   * This service is being deactivated so unregister upstream-downstream
   * dependencies, triggers, matchers and listeners.
   *
   * @param context
   */
  protected void deactivate(final ComponentContext context) {
    MetadataDependencyRegistry registry = this.registryTracker.getService();
    registry.removeNotificationListener(this);
    registry.deregisterDependency(PhysicalTypeIdentifier.getMetadataIdentiferType(),
        getProvidesType());
    this.registryTracker.close();

    removeMetadataTrigger(ROO_CONTROLLER);

    CustomDataKeyDecorator keyDecorator = this.keyDecoratorTracker.getService();
    keyDecorator.unregisterMatchers(getClass());
    this.keyDecoratorTracker.close();
  }

  @Override
  protected String createLocalIdentifier(final JavaType javaType, final LogicalPath path) {
    return ControllerMetadata.createIdentifier(javaType, path);
  }

  @Override
  protected String getGovernorPhysicalTypeIdentifier(final String metadataIdentificationString) {
    final JavaType javaType = ControllerMetadata.getJavaType(metadataIdentificationString);
    final LogicalPath path = ControllerMetadata.getPath(metadataIdentificationString);
    return PhysicalTypeIdentifier.createIdentifier(javaType, path);
  }

  public String getItdUniquenessFilenameSuffix() {
    return "Controller";
  }

  @Override
  protected String getLocalMidToRequest(final ItdTypeDetails itdTypeDetails) {
    // Determine the governor for this ITD, and whether any metadata is even
    // hoping to hear about changes to that JavaType and its ITDs
    final JavaType governor = itdTypeDetails.getName();
    final String localMid = domainTypeToServiceMidMap.get(governor);
    if (localMid != null) {
      return localMid;
    }

    final MemberHoldingTypeDetails memberHoldingTypeDetails =
        getTypeLocationService().getTypeDetails(governor);
    if (memberHoldingTypeDetails != null) {
      for (final JavaType type : memberHoldingTypeDetails.getLayerEntities()) {
        final String localMidType = domainTypeToServiceMidMap.get(type);
        if (localMidType != null) {
          return localMidType;
        }
      }
    }
    return null;
  }

  @Override
  protected ItdTypeDetailsProvidingMetadataItem getMetadata(
      final String metadataIdentificationString, final JavaType aspectName,
      final PhysicalTypeMetadata governorPhysicalTypeMetadata, final String itdFilename) {

    ControllerAnnotationValues controllerValues =
        new ControllerAnnotationValues(governorPhysicalTypeMetadata);

    // Getting entity
    final JavaType entity = controllerValues.getEntity();

    ClassOrInterfaceTypeDetails entityDetails = getTypeLocationService().getTypeDetails(entity);
    // Get entity metadata
    final String entityMetadataId = JpaEntityMetadata.createIdentifier(entityDetails);
    registerDependency(entityMetadataId, metadataIdentificationString);
    final JpaEntityMetadata entityMetadata = getMetadataService().get(entityMetadataId);

    // Getting type
    ControllerType type = controllerValues.getType();

    // Getting pathPrefix
    String pathPrefix = "";
    if (controllerValues.getPathPrefix() != null) {
      pathPrefix = StringUtils.lowerCase(controllerValues.getPathPrefix());
    }

    // Getting related service
    ClassOrInterfaceTypeDetails serviceDetails = getServiceLocator().getService(entity);
    JavaType service = serviceDetails.getType();
    final String serviceMetadataId = ServiceMetadata.createIdentifier(serviceDetails);
    registerDependency(serviceMetadataId, metadataIdentificationString);
    final ServiceMetadata serviceMetadata = getMetadataService().get(serviceMetadataId);

    // Generate path
    String path = "/".concat(StringUtils.lowerCase(getPluralService().getPlural(entity)));
    if (StringUtils.isNotEmpty(pathPrefix)) {
      if (!pathPrefix.startsWith("/")) {
        pathPrefix = "/".concat(pathPrefix);
      }
      path = pathPrefix.concat(path);
    }



    RelationInfo detailsFieldInfo = null;
    JavaType detailsService = null;
    ServiceMetadata detailsServiceMetadata = null;

    if (type == ControllerType.DETAIL) {
      // TODO
      // generate detail info object
      detailsFieldInfo = getControllerDetailInfo(governorPhysicalTypeMetadata, entityMetadata);

      // Getting related service
      ClassOrInterfaceTypeDetails detailsServiceDetails =
          getServiceLocator().getService(detailsFieldInfo.childType);
      detailsService = detailsServiceDetails.getType();
      final String detailServiceMetadataId =
          ServiceMetadata.createIdentifier(detailsServiceDetails);
      registerDependency(detailServiceMetadataId, metadataIdentificationString);
      detailsServiceMetadata = getMetadataService().get(detailServiceMetadataId);

    }

    return new ControllerMetadata(metadataIdentificationString, aspectName, controllerValues,
        governorPhysicalTypeMetadata, entity, entityMetadata, service, detailsService, path, type,
        serviceMetadata, detailsServiceMetadata, detailsFieldInfo);
  }

  private void registerDependency(final String upstreamDependency, final String downStreamDependency) {

    if (getMetadataDependencyRegistry() != null
        && StringUtils.isNotBlank(upstreamDependency)
        && StringUtils.isNotBlank(downStreamDependency)
        && !upstreamDependency.equals(downStreamDependency)
        && !MetadataIdentificationUtils.getMetadataClass(downStreamDependency).equals(
            MetadataIdentificationUtils.getMetadataClass(upstreamDependency))) {
      getMetadataDependencyRegistry().registerDependency(upstreamDependency, downStreamDependency);
    }
  }

  /**
   * Get necesary information about detail to create a detail controller
   *
   * @param governorPhysicalTypeMetadata the governor, which is expected to
     *            contain a {@link ClassOrInterfaceTypeDetails}
   * @param path Path parent controller
   * @param entity Parent entity
   *
   * @return Information about detail
   */
  private RelationInfo getControllerDetailInfo(
      final PhysicalTypeMetadata governorPhysicalTypeMetadata,
      final JpaEntityMetadata entityMetadata) {
    final JavaType controller =
        governorPhysicalTypeMetadata.getMemberHoldingTypeDetails().getName();

    // Getting the relationField from @RooDetail entity
    final AnnotationAttributeValue<Object> relationFieldAttr =
        governorPhysicalTypeMetadata.getMemberHoldingTypeDetails()
            .getAnnotation(RooJavaType.ROO_DETAIL).getAttribute("relationField");

    Validate.notNull(relationFieldAttr,
        "ERROR: In %s controller, @RooDetail annotation must have relationField value", controller);

    final String relationField = (String) relationFieldAttr.getValue();

    Validate.isTrue(StringUtils.isNotBlank(relationField),
        "ERROR: In %s controller, @RooDetail annotation must have relationField value", controller);
    final RelationInfo info = entityMetadata.getRelationInfos().get(relationField);

    Validate.notNull(info,
        "ERROR: In %s controller, @RooDetail.relationField '%s' not found on '%s' entity",
        controller, relationField, entityMetadata.getDestination());

    Validate
        .isTrue(
            info.cardinality == Cardinality.ONE_TO_MANY
                || info.cardinality == Cardinality.MANY_TO_MANY,
            "ERROR: In %s controller, @RooDetail.relationField '%s' has unsupported type (%s) on '%s' entity: should be ONE_TO_MANY or MANY_TO_MANY",
            controller, relationField, info.cardinality.name(), entityMetadata.getDestination());

    return info;
  }

  /**
   * Get the entity that represents a relation field
   *
   * @param relationField Array that represents the relation field.
   * @param entity Current entity to search the corresponding field
   * @param level Current array level to search
   * @return
   */
  private JavaType getEntityFromRelationField(String[] relationField, JavaType entity, int level) {
    JavaType entityTypeToCalculate = null;
    MemberDetails memberDetails = getMemberDetails(entity);
    List<FieldMetadata> fields = memberDetails.getFields();
    for (FieldMetadata entityField : fields) {
      if (entityField.getFieldName().getSymbolName().equals(relationField[level])) {

        AnnotationMetadata oneToManyAnnotation = entityField.getAnnotation(JpaJavaType.ONE_TO_MANY);

        if (oneToManyAnnotation != null
            && (entityField.getFieldType().getFullyQualifiedTypeName()
                .equals(JavaType.LIST.getFullyQualifiedTypeName()) || entityField.getFieldType()
                .getFullyQualifiedTypeName().equals(JavaType.SET.getFullyQualifiedTypeName()))) {
          level++;
          if (relationField.length > level) {
            entityTypeToCalculate =
                getEntityFromRelationField(relationField, entityField.getFieldType()
                    .getParameters().get(0), level);
          } else {
            entityTypeToCalculate = entityField.getFieldType().getParameters().get(0);
          }
          break;
        }
      }
    }
    return entityTypeToCalculate;
  }

  public String getProvidesType() {
    return ControllerMetadata.getMetadataIdentiferType();
  }

  // OSGI Services

  private PluralService getPluralService() {
    return serviceInstaceManager.getServiceInstance(this, PluralService.class);
  }

  private ServiceLocator getServiceLocator() {
    return serviceInstaceManager.getServiceInstance(this, ServiceLocator.class);
  }
}
