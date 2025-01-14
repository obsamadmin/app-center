/*
 * This file is part of the Meeds project (https://meeds.io/).
 * Copyright (C) 2020 Meeds Association
 * contact@meeds.io
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.exoplatform.appcenter.service;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

import org.exoplatform.appcenter.storage.ApplicationCenterStorage;
import org.exoplatform.services.organization.*;
import org.exoplatform.services.organization.idm.MembershipImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.picocontainer.Startable;

import org.exoplatform.appcenter.dao.ApplicationDAO;
import org.exoplatform.appcenter.dao.FavoriteApplicationDAO;
import org.exoplatform.appcenter.dto.*;
import org.exoplatform.appcenter.plugin.ApplicationPlugin;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.commons.file.services.NameSpaceService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.RootContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ObjectParameter;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.naming.InitialContextInitializer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.MembershipEntry;

public class ApplicationCenterServiceTest {

  private static final String      ADMIN_USERNAME  = "admin";

  private static final String      SIMPLE_USERNAME = "simple";

  private ConversationState        adminState;

  private ConversationState        simpleState;

  private ApplicationCenterService applicationCenterService;

  private ApplicationCenterStorage appCenterStorage;

  @BeforeClass
  @SuppressWarnings("deprecation")
  public static void startDB() {
    RootContainer rootContainer = RootContainer.getInstance();
    InitialContextInitializer initializer = rootContainer.getComponentInstanceOfType(InitialContextInitializer.class);
    initializer.recall(); // NOSONAR
  }

  @Before
  @SuppressWarnings("deprecation")
  public void setup() throws Exception {
    PortalContainer container = PortalContainer.getInstance();
    assertNotNull(container);

    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    applicationCenterService = ExoContainerContext.getService(ApplicationCenterService.class);
    appCenterStorage = ExoContainerContext.getService(ApplicationCenterStorage.class);

    OrganizationService organizationService = ExoContainerContext.getService(OrganizationService.class);
    UserHandler userHandler = organizationService.getUserHandler();
    MembershipHandler membershipHandler = organizationService.getMembershipHandler();

    User adminUser = userHandler.findUserByName(ADMIN_USERNAME);
    if (adminUser == null) {
      adminUser = userHandler.createUserInstance(ADMIN_USERNAME);
      userHandler.createUser(adminUser, true);

      Membership membership = new MembershipImpl("member:" + ADMIN_USERNAME + ":"
          + ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);
      membershipHandler.createMembership(membership, true); // NOSONAR
    }
    User simpleUser = userHandler.findUserByName(SIMPLE_USERNAME);
    if (simpleUser == null) {
      simpleUser = userHandler.createUserInstance(SIMPLE_USERNAME);
      userHandler.createUser(simpleUser, true);
    }

    // Workaround, the service wasn't started
    Startable fileNameSpaceService = (Startable) ExoContainerContext.getService(NameSpaceService.class);
    assertNotNull(fileNameSpaceService);
    fileNameSpaceService.start();

    Collection<MembershipEntry> adminMemberships = new ArrayList<MembershipEntry>();
    adminMemberships.add(new MembershipEntry(ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP, "*"));
    adminMemberships.add(new MembershipEntry(ApplicationCenterService.DEFAULT_USERS_GROUP, "*"));
    Collection<MembershipEntry> simpleMemberships = new ArrayList<MembershipEntry>();
    simpleMemberships.add(new MembershipEntry(ApplicationCenterService.DEFAULT_USERS_GROUP, "*"));

    Identity adminIdentity = new Identity(ADMIN_USERNAME, adminMemberships);
    Identity simpleIdentity = new Identity(SIMPLE_USERNAME, simpleMemberships);

    adminState = new ConversationState(adminIdentity);
    simpleState = new ConversationState(simpleIdentity);
  }

  @After
  public void teardown() {
    FavoriteApplicationDAO favoriteApplicationDAO = ExoContainerContext.getService(FavoriteApplicationDAO.class);
    favoriteApplicationDAO.deleteAll();

    ApplicationDAO applicationDAO = ExoContainerContext.getService(ApplicationDAO.class);
    applicationDAO.deleteAll();

    ConversationState.setCurrent(null);

    RequestLifeCycle.end();
    ExoContainerContext.setCurrentContainer(null);
  }

  @Test
  public void testCreateApplication() throws Exception {
    try {
      applicationCenterService.createApplication(null);
      fail("Shouldn't allow to add null application");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    Application application = new Application(null,
                                              "title",
                                              "url",
                                              "",
                                              0L,
                                              0L,
                                              null,
                                              null,
                                              "description",
                                              false,
                                              true,
                                              false,
                                              false,
                                              false,
                                              "permissions1",
                                              "permissions2");

    Application storedApplication = applicationCenterService.createApplication(application);
    assertNotNull(storedApplication);
    assertNotNull(storedApplication.getId());
    assertEquals(application.getTitle(), storedApplication.getTitle());
    assertEquals(application.getUrl(), storedApplication.getUrl());
    assertEquals(application.getImageFileId(), storedApplication.getImageFileId());
    assertEquals(application.getDescription(), storedApplication.getDescription());
    assertEquals(application.isActive(), storedApplication.isActive());
    assertEquals(application.isMandatory(), storedApplication.isMandatory());
    assertEquals(application.getPermissions(), storedApplication.getPermissions());

    try {
      applicationCenterService.createApplication(storedApplication);
      fail("Shouldn't allow to create the application again");
    } catch (ApplicationAlreadyExistsException e) {
      // Expected
    }
  }

  @Test
  public void testUpdateApplication() throws Exception {
    try {
      applicationCenterService.updateApplication(null, null);
      fail("Shouldn't allow to add null arguments");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      applicationCenterService.updateApplication(null, "testuser");
      fail("Shouldn't allow to add null application");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    Application application = new Application(null,
                                              "title",
                                              "url",
                                              "",
                                              0L,
                                              0L,
                                              null,
                                              null,
                                              "description",
                                              false,
                                              true,
                                              false,
                                              false,
                                              false,
                                              ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);

    try {
      applicationCenterService.updateApplication(application, null);
      fail("Shouldn't allow to add null user");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    ConversationState.setCurrent(adminState);
    try {
      applicationCenterService.updateApplication(application, ADMIN_USERNAME);
      fail("Shouldn't allow to update non existing application");
    } catch (ApplicationNotFoundException e) {
      // Expected
    }

    try {
      application.setId(50000L);
      applicationCenterService.updateApplication(application, ADMIN_USERNAME);
      fail("Shouldn't allow to update non existing application");
    } catch (ApplicationNotFoundException e) {
      // Expected
    } finally {
      application.setId(null);
    }

    Application storedApplication = applicationCenterService.createApplication(application);

    application.setId(storedApplication.getId());
    application.setTitle("title2");
    storedApplication = applicationCenterService.updateApplication(application, ADMIN_USERNAME);

    assertNotNull(storedApplication);
    assertNotNull(storedApplication.getId());
    assertEquals(application.getTitle(), storedApplication.getTitle());
    assertEquals(application.getUrl(), storedApplication.getUrl());
    assertEquals(application.getImageFileId(), storedApplication.getImageFileId());
    assertEquals(application.getDescription(), storedApplication.getDescription());
    assertEquals(application.isActive(), storedApplication.isActive());
    assertEquals(application.isMandatory(), storedApplication.isMandatory());
    assertEquals(application.getPermissions(), storedApplication.getPermissions());

    ConversationState.setCurrent(simpleState);
    try {
      storedApplication = applicationCenterService.updateApplication(application, SIMPLE_USERNAME);
      fail("Simple user shouldn't be able to update non authorized application");
    } catch (IllegalAccessException e) {
      // Expected
    }

    ConversationState.setCurrent(adminState);
    application.setPermissions(ApplicationCenterService.DEFAULT_USERS_PERMISSION);
    storedApplication = applicationCenterService.updateApplication(application, ADMIN_USERNAME);
    assertEquals(ApplicationCenterService.DEFAULT_USERS_PERMISSION, storedApplication.getPermissions().get(0));

    application.setUrl("url2");
    storedApplication = applicationCenterService.updateApplication(application, ADMIN_USERNAME);
    assertEquals("url2", storedApplication.getUrl());
  }

  @Test
  public void testDeleteApplication() throws Exception {
    try {
      applicationCenterService.deleteApplication(null, null);
      fail("Shouldn't allow to add null arguments");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      applicationCenterService.deleteApplication(null, "testuser");
      fail("Shouldn't allow to add null application");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      applicationCenterService.deleteApplication(50000L, null);
      fail("Shouldn't allow to add null user");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    ConversationState.setCurrent(adminState);
    try {
      applicationCenterService.deleteApplication(50000L, ADMIN_USERNAME);
      fail("Shouldn't allow to update non existing application");
    } catch (ApplicationNotFoundException e) {
      // Expected
    }

    Application application = new Application(null,
                                              "title",
                                              "url",
                                              "",
                                              0L,
                                              0L,
                                              null,
                                              null,
                                              "description",
                                              false,
                                              true,
                                              false,
                                              false,
                                              false,
                                              ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);
    Application storedApplication = applicationCenterService.createApplication(application);
    applicationCenterService.deleteApplication(storedApplication.getId(), ADMIN_USERNAME);

    application = new Application(null,
                                  "title",
                                  "url",
                                  "",
                                  0L,
                                  0L,
                                  null,
                                  null,
                                  "description",
                                  false,
                                  true,
                                  false,
                                  false,
                                  false,
                                  ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);
    storedApplication = applicationCenterService.createApplication(application);
    ConversationState.setCurrent(simpleState);
    try {
      applicationCenterService.deleteApplication(storedApplication.getId(), SIMPLE_USERNAME);
      fail("Simple user shouldn't be able to delete non authorized application");
    } catch (IllegalAccessException e1) {
      // Expected
    }

    ConversationState.setCurrent(adminState);
    application.setId(storedApplication.getId());
    application.setPermissions(ApplicationCenterService.DEFAULT_USERS_PERMISSION);
    storedApplication = applicationCenterService.updateApplication(application, ADMIN_USERNAME);

    applicationCenterService.deleteApplication(storedApplication.getId(), ADMIN_USERNAME);
  }

  @Test
  public void testMaxFavoriteApps() {
    long originalMaxFavoriteApps = applicationCenterService.getMaxFavoriteApps();

    applicationCenterService.setMaxFavoriteApps(originalMaxFavoriteApps + 1);
    assertEquals(originalMaxFavoriteApps + 1, applicationCenterService.getMaxFavoriteApps());

    applicationCenterService.setMaxFavoriteApps(0);
    assertEquals(0, applicationCenterService.getMaxFavoriteApps());
  }

  @Test
  public void testSetDefaultAppImage() throws Exception {
    ApplicationImage applicationImage = applicationCenterService.setDefaultAppImage(null);
    assertNull(applicationImage);

    applicationImage = new ApplicationImage(null, null, null);
    applicationImage = applicationCenterService.setDefaultAppImage(applicationImage);
    assertNull(applicationImage);

    applicationImage = new ApplicationImage(null, "name", "content");
    applicationImage = applicationCenterService.setDefaultAppImage(applicationImage);
    assertNotNull(applicationImage);
    assertNotNull(applicationImage.getId());

    ApplicationImage storedApplicationImage = applicationCenterService.setDefaultAppImage(applicationImage);
    assertNotNull(storedApplicationImage);
    assertEquals(applicationImage.getId(), storedApplicationImage.getId());
  }

  @Test
  public void testGetAppGeneralSettings() throws Exception {
    applicationCenterService.setDefaultAppImage(null);
    applicationCenterService.setMaxFavoriteApps(0);

    GeneralSettings generalSettings = applicationCenterService.getAppGeneralSettings();
    assertNotNull(generalSettings);
    assertEquals(0, generalSettings.getMaxFavoriteApps());
    assertNull(generalSettings.getDefaultApplicationImage());

    applicationCenterService.setDefaultAppImage(new ApplicationImage(null, null, null));
    generalSettings = applicationCenterService.getAppGeneralSettings();

    assertEquals(0, generalSettings.getMaxFavoriteApps());
    assertNull(generalSettings.getDefaultApplicationImage());

    applicationCenterService.setDefaultAppImage(new ApplicationImage(null, "name", "content"));

    generalSettings = applicationCenterService.getAppGeneralSettings();
    assertEquals(0, generalSettings.getMaxFavoriteApps());
    assertNotNull(generalSettings.getDefaultApplicationImage());
    assertEquals("name", generalSettings.getDefaultApplicationImage().getFileName());
    assertFalse(generalSettings.getDefaultApplicationImage().getFileBody().isEmpty());
    assertNotNull(generalSettings.getDefaultApplicationImage().getId());
  }

  @Test
  public void testAddFavoriteApplication() throws Exception {
    ConversationState.setCurrent(adminState);
    try {
      applicationCenterService.addFavoriteApplication(0, null);
      fail("Shouldn't allow to add null arguments");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    ConversationState.setCurrent(simpleState);
    try {
      applicationCenterService.addFavoriteApplication(0, SIMPLE_USERNAME);
      fail("Shouldn't allow to add zero as applicationId");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      applicationCenterService.addFavoriteApplication(5000L, SIMPLE_USERNAME);
      fail("Shouldn't allow to add an application as favorite while it doesn't exist");
    } catch (ApplicationNotFoundException e) {
      // Expected
    }

    Application application = new Application(null,
                                              "title",
                                              "url",
                                              "",
                                              0L,
                                              0L,
                                              null,
                                              null,
                                              "description",
                                              false,
                                              true,
                                              false,
                                              false,
                                              false,
                                              ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);
    Application storedApplication = applicationCenterService.createApplication(application);
    ConversationState.setCurrent(simpleState);
    try {
      applicationCenterService.addFavoriteApplication(storedApplication.getId(), SIMPLE_USERNAME);
      fail("Shouldn't allow to add an application as favorite while user doesn't have access to it");
    } catch (IllegalAccessException e) {
      // Expected
    }

    ConversationState.setCurrent(adminState);
    applicationCenterService.addFavoriteApplication(storedApplication.getId(), ADMIN_USERNAME);
    storedApplication.setPermissions(ApplicationCenterService.DEFAULT_USERS_PERMISSION);
    storedApplication = applicationCenterService.updateApplication(storedApplication, ADMIN_USERNAME);
    ConversationState.setCurrent(simpleState);
    applicationCenterService.addFavoriteApplication(storedApplication.getId(), SIMPLE_USERNAME);
  }

  @Test
  public void testDeleteFavoriteApplication() throws Exception {
    try {
      applicationCenterService.deleteFavoriteApplication(null, null);
      fail("Shouldn't allow to add null arguments");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    ConversationState.setCurrent(simpleState);
    try {
      applicationCenterService.deleteFavoriteApplication(0L, SIMPLE_USERNAME);
      fail("Shouldn't allow to add zero as applicationId");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    applicationCenterService.deleteFavoriteApplication(5000L, SIMPLE_USERNAME);

    Application application = new Application(null,
                                              "title",
                                              "url",
                                              "",
                                              0L,
                                              0L,
                                              null,
                                              null,
                                              "description",
                                              false,
                                              true,
                                              false,
                                              false,
                                              false,
                                              ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);
    Application storedApplication = applicationCenterService.createApplication(application);
    applicationCenterService.deleteFavoriteApplication(storedApplication.getId(), SIMPLE_USERNAME);
    ConversationState.setCurrent(adminState);
    applicationCenterService.deleteFavoriteApplication(storedApplication.getId(), ADMIN_USERNAME);

    applicationCenterService.addFavoriteApplication(storedApplication.getId(), ADMIN_USERNAME);
    applicationCenterService.deleteFavoriteApplication(storedApplication.getId(), ADMIN_USERNAME);

    storedApplication.setPermissions(ApplicationCenterService.DEFAULT_USERS_PERMISSION);
    storedApplication = applicationCenterService.updateApplication(storedApplication, ADMIN_USERNAME);

    ConversationState.setCurrent(simpleState);
    applicationCenterService.addFavoriteApplication(storedApplication.getId(), SIMPLE_USERNAME);
    applicationCenterService.deleteFavoriteApplication(storedApplication.getId(), SIMPLE_USERNAME);
  }

  @Test
  public void testGetApplicationsList() throws Exception {
    ApplicationList applicationsList = applicationCenterService.getApplicationsList(0, 0, null);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(0, applicationsList.getApplications().size());
    assertEquals(0, applicationsList.getSize());
    assertEquals(0, applicationsList.getOffset());
    assertEquals(0, applicationsList.getLimit());

    Application application = new Application(null,
                                              "title",
                                              "url",
                                              "",
                                              0L,
                                              0L,
                                              null,
                                              null,
                                              "description",
                                              false,
                                              true,
                                              false,
                                              false,
                                              false,
                                              ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);
    applicationCenterService.createApplication(application);

    Application application2 = new Application(null,
                                               "title2",
                                               "url2",
                                               "",
                                               0L,
                                               0L,
                                               null,
                                               null,
                                               "description",
                                               false,
                                               true,
                                               false,
                                               false,
                                               false,
                                               ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);
    applicationCenterService.createApplication(application2);

    applicationsList = applicationCenterService.getApplicationsList(0, 0, null);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(2, applicationsList.getApplications().size());
    assertEquals(2, applicationsList.getSize());

    applicationsList = applicationCenterService.getApplicationsList(1, 0, null);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(1, applicationsList.getApplications().size());
    assertEquals(1, applicationsList.getSize());

    applicationsList = applicationCenterService.getApplicationsList(2, 0, null);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(0, applicationsList.getApplications().size());
    assertEquals(0, applicationsList.getSize());

    applicationsList = applicationCenterService.getApplicationsList(3, 0, null);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(0, applicationsList.getApplications().size());
    assertEquals(0, applicationsList.getSize());

    applicationsList = applicationCenterService.getApplicationsList(0, 10, null);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(2, applicationsList.getApplications().size());
    assertEquals(2, applicationsList.getSize());
  }

  @Test
  public void testGetMandatoryAndFavoriteApplicationsList() throws Exception {
    Application application1 = new Application(null,
                                               "title1",
                                               "url1",
                                               "",
                                               0L,
                                               0L,
                                               null,
                                               null,
                                               "description1",
                                               false,
                                               true,
                                               true,
                                               true,
                                               false,
                                               ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);

    Application application2 = new Application(null,
                                               "title2",
                                               "url2",
                                               "",
                                               0L,
                                               0L,
                                               null,
                                               null,
                                               "description",
                                               false,
                                               false,
                                               true,
                                               true,
                                               false,
                                               ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);

    Application application3 = new Application(null,
                                               "title3",
                                               "url3",
                                               "",
                                               0L,
                                               0L,
                                               null,
                                               null,
                                               "description3",
                                               false,
                                               true,
                                               false,
                                               false,
                                               false,
                                               ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);

    Application application4 = new Application(null,
                                               "title4",
                                               "url4",
                                               "",
                                               0L,
                                               0L,
                                               null,
                                               null,
                                               "description4",
                                               false,
                                               true,
                                               false,
                                               false,
                                               false,
                                               ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);

    Application application5 = new Application(null,
                                               "title5",
                                               "url5",
                                               "",
                                               0L,
                                               0L,
                                               null,
                                               null,
                                               "description5",
                                               false,
                                               true,
                                               false,
                                               false,
                                               false,
                                               ApplicationCenterService.DEFAULT_USERS_PERMISSION);

    applicationCenterService.createApplication(application1);
    applicationCenterService.createApplication(application2);
    Application storedApp3 = applicationCenterService.createApplication(application3);
    Application storedApp4 = applicationCenterService.createApplication(application4);
    Application storedApp5 = applicationCenterService.createApplication(application5);

    applicationCenterService.addFavoriteApplication(storedApp3.getId(), "admin");
    applicationCenterService.addFavoriteApplication(storedApp4.getId(), "admin");
    applicationCenterService.addFavoriteApplication(storedApp5.getId(), "simple");

    ApplicationList MandatoryAndFavoriteApplications = applicationCenterService.getMandatoryAndFavoriteApplicationsList("admin");
    assertEquals(3, MandatoryAndFavoriteApplications.getApplications().size());
    assertEquals(3, MandatoryAndFavoriteApplications.getSize());
  }

  @Test
  public void testUpdateFavoriteApplicationOrder() throws Exception {
    Application application1 = new Application(null,
                                               "title3",
                                               "url3",
                                               "",
                                               0L,
                                               0L,
                                               null,
                                               null,
                                               "description3",
                                               false,
                                               true,
                                               false,
                                               false,
                                               false,
                                               ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);

    Application application2 = new Application(null,
                                               "title5",
                                               "url5",
                                               "",
                                               0L,
                                               0L,
                                               null,
                                               null,
                                               "description5",
                                               false,
                                               true,
                                               false,
                                               false,
                                               false,
                                               ApplicationCenterService.DEFAULT_USERS_PERMISSION);

    try {
      applicationCenterService.getMandatoryAndFavoriteApplicationsList("");
      fail("Shouldn't retrieve applications with null username");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    Application storedApp1 = applicationCenterService.createApplication(application1);
    Application storedApp2 = applicationCenterService.createApplication(application2);

    applicationCenterService.addFavoriteApplication(storedApp1.getId(), "admin");
    applicationCenterService.addFavoriteApplication(storedApp2.getId(), "simple");

    applicationCenterService.updateFavoriteApplicationOrder(new ApplicationOrder(storedApp1.getId(), new Long(1)), "admin");
    applicationCenterService.updateFavoriteApplicationOrder(new ApplicationOrder(storedApp2.getId(), new Long(2)), "simple");

    try {
      applicationCenterService.updateFavoriteApplicationOrder(new ApplicationOrder(storedApp1.getId(), new Long(1)), "");
      fail("Shouldn't retrieve applications with null username");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      applicationCenterService.updateFavoriteApplicationOrder(new ApplicationOrder(0L, new Long(1)), "");
      fail("Application id can not be negative or equal to zero.");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    assertEquals(new Long(1),
                 applicationCenterService.getMandatoryAndFavoriteApplicationsList("admin").getApplications().get(0).getOrder());
    assertEquals(new Long(1),
                 applicationCenterService.getMandatoryAndFavoriteApplicationsList("admin").getApplications().get(0).getOrder());

  }

  @Test
  public void testGetAuthorizedApplicationsList() throws Exception {
    try {
      applicationCenterService.getAuthorizedApplicationsList(0, 0, null, null);
      fail("Shouldn't retrieve applications with null username");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    ConversationState.setCurrent(simpleState);
    ApplicationList applicationsList = applicationCenterService.getAuthorizedApplicationsList(0, 0, null, SIMPLE_USERNAME);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(0, applicationsList.getApplications().size());
    assertEquals(0, applicationsList.getSize());

    Application application = new Application(null,
                                              "title",
                                              "url",
                                              "",
                                              0L,
                                              0L,
                                              null,
                                              null,
                                              "description",
                                              false,
                                              true,
                                              false,
                                              false,
                                              false,
                                              ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);
    ConversationState.setCurrent(adminState);
    applicationCenterService.createApplication(application);

    Application application2 = new Application(null,
                                               "title2",
                                               "url2",
                                               "",
                                               0L,
                                               0L,
                                               null,
                                               null,
                                               "description",
                                               false,
                                               true,
                                               false,
                                               false,
                                               false,
                                               ApplicationCenterService.DEFAULT_USERS_PERMISSION);
    applicationCenterService.createApplication(application2);

    applicationsList = applicationCenterService.getAuthorizedApplicationsList(0, 0, null, ADMIN_USERNAME);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(2, applicationsList.getApplications().size());
    assertEquals(2, applicationsList.getSize());

    ConversationState.setCurrent(simpleState);
    applicationsList = applicationCenterService.getAuthorizedApplicationsList(0, 0, null, SIMPLE_USERNAME);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(1, applicationsList.getApplications().size());
    assertEquals(1, applicationsList.getSize());

    ConversationState.setCurrent(adminState);
    applicationsList = applicationCenterService.getAuthorizedApplicationsList(1, 0, null, ADMIN_USERNAME);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(1, applicationsList.getApplications().size());
    assertEquals(1, applicationsList.getSize());

    ConversationState.setCurrent(simpleState);
    applicationsList = applicationCenterService.getAuthorizedApplicationsList(1, 0, null, SIMPLE_USERNAME);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(0, applicationsList.getApplications().size());
    assertEquals(0, applicationsList.getSize());

    ConversationState.setCurrent(adminState);
    applicationsList = applicationCenterService.getAuthorizedApplicationsList(2, 0, null, ADMIN_USERNAME);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(0, applicationsList.getApplications().size());
    assertEquals(0, applicationsList.getSize());

    ConversationState.setCurrent(simpleState);
    applicationsList = applicationCenterService.getAuthorizedApplicationsList(2, 0, null, SIMPLE_USERNAME);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(0, applicationsList.getApplications().size());
    assertEquals(0, applicationsList.getSize());

    ConversationState.setCurrent(adminState);
    applicationsList = applicationCenterService.getAuthorizedApplicationsList(3, 0, null, ADMIN_USERNAME);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(0, applicationsList.getApplications().size());
    assertEquals(0, applicationsList.getSize());

    ConversationState.setCurrent(simpleState);
    applicationsList = applicationCenterService.getAuthorizedApplicationsList(3, 0, null, SIMPLE_USERNAME);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(0, applicationsList.getApplications().size());
    assertEquals(0, applicationsList.getSize());

    ConversationState.setCurrent(adminState);
    applicationsList = applicationCenterService.getAuthorizedApplicationsList(0, 10, null, ADMIN_USERNAME);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(2, applicationsList.getApplications().size());
    assertEquals(2, applicationsList.getSize());

    ConversationState.setCurrent(simpleState);
    applicationsList = applicationCenterService.getAuthorizedApplicationsList(0, 10, null, SIMPLE_USERNAME);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(1, applicationsList.getApplications().size());
    assertEquals(1, applicationsList.getSize());
  }

  @Test
  public void testGetLastUpdated() throws Exception {
    long currentTimeMillis = System.currentTimeMillis();
    try {
      applicationCenterService.getApplicationImageLastUpdated(50000L, null);
      fail("Shouldn't allow to use null user");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    ConversationState.setCurrent(simpleState);
    try {
      applicationCenterService.getApplicationImageLastUpdated(50000L, SIMPLE_USERNAME);
      fail("Shouldn't allow to get not found application");
    } catch (ApplicationNotFoundException e) {
      // Expected
    }

    Application application = new Application(null,
                                              "title",
                                              "url",
                                              "",
                                              5L,
                                              0L,
                                              null,
                                              null,
                                              "description",
                                              false,
                                              true,
                                              false,
                                              false,
                                              false,
                                              ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);
    application.setImageFileName("name");
    application.setImageFileBody("content");
    Application storedApplication = applicationCenterService.createApplication(application);

    ConversationState.setCurrent(simpleState);
    try {
      applicationCenterService.getApplicationImageLastUpdated(storedApplication.getId(), SIMPLE_USERNAME);
      fail("Shouldn't allow to get illustration of non authorized application");
    } catch (IllegalAccessException e) {
      // Expected
    }

    ConversationState.setCurrent(adminState);
    Long lastUpdated = applicationCenterService.getApplicationImageLastUpdated(storedApplication.getId(), ADMIN_USERNAME);
    assertNotNull(lastUpdated);
    assertTrue(lastUpdated >= currentTimeMillis);
  }

  @Test
  public void testGetImageStream() throws Exception {
    try {
      applicationCenterService.getApplicationImageInputStream(50000L, null);
      fail("Shouldn't allow to use null user");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    ConversationState.setCurrent(simpleState);
    try {
      applicationCenterService.getApplicationImageInputStream(50000L, SIMPLE_USERNAME);
      fail("Shouldn't allow to get not found application");
    } catch (ApplicationNotFoundException e) {
      // Expected
    }

    Application application = new Application(null,
                                              "title",
                                              "url",
                                              "",
                                              5L,
                                              0L,
                                              null,
                                              null,
                                              "description",
                                              false,
                                              true,
                                              false,
                                              false,
                                              false,
                                              ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);
    application.setImageFileName("name");
    application.setImageFileBody("content");
    Application storedApplication = applicationCenterService.createApplication(application);

    ConversationState.setCurrent(simpleState);
    try {
      applicationCenterService.getApplicationImageInputStream(storedApplication.getId(), SIMPLE_USERNAME);
      fail("Shouldn't allow to get illustration of non authorized application");
    } catch (IllegalAccessException e) {
      // Expected
    }

    ConversationState.setCurrent(adminState);
    InputStream inputStream = applicationCenterService.getApplicationImageInputStream(storedApplication.getId(), ADMIN_USERNAME);
    assertNotNull(inputStream);
    assertTrue(inputStream.available() > 0);
  }

  @Test
  public void testAddApplicationPlugin() throws FileStorageException {
    try {
      applicationCenterService.addApplicationPlugin(null);
      fail("Shouldn't be able to add null plugin");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      new ApplicationPlugin(null);
      fail("Shouldn't be able to add null params in plugin");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    InitParams params = new InitParams();
    try {
      new ApplicationPlugin(params);
      fail("Shouldn't be able to add null application in parameter of plugin");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    Application application = new Application(null,
                                              "title",
                                              "url",
                                              "",
                                              5L,
                                              0L,
                                              null,
                                              null,
                                              "description",
                                              false,
                                              true,
                                              false,
                                              false,
                                              true,
                                              ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);

    ObjectParameter applicationParam = new ObjectParameter();
    applicationParam.setName("application");
    applicationParam.setObject(application);
    params.addParameter(applicationParam);

    ApplicationPlugin applicationPlugin = new ApplicationPlugin(params);
    assertNotNull(applicationPlugin.getApplication());
    assertNull(applicationPlugin.getImagePath());

    try {
      applicationCenterService.addApplicationPlugin(applicationPlugin);
      fail("Shouldn't be able to add plugin with null name");
    } catch (IllegalStateException e) {
      // Expected
    }

    String pluginName = "testapp";
    applicationPlugin.setName(pluginName);
    try {
      applicationCenterService.addApplicationPlugin(applicationPlugin);
      applicationCenterService.start();

      ApplicationList applicationsList = applicationCenterService.getApplicationsList(0, 0, null);
      assertNotNull(applicationsList);
      assertNotNull(applicationsList.getApplications());
      assertEquals(1, applicationsList.getApplications().size());

      Application storedApplication = applicationsList.getApplications().get(0);

      assertNotNull(storedApplication);
      assertNotNull(storedApplication.getId());
      assertEquals(application.getTitle(), storedApplication.getTitle());
      assertEquals(application.getUrl(), storedApplication.getUrl());
      assertEquals(application.getDescription(), storedApplication.getDescription());
      assertEquals(application.isActive(), storedApplication.isActive());
      assertEquals(application.isMandatory(), storedApplication.isMandatory());
      assertTrue(storedApplication.isSystem());
      assertEquals(application.getPermissions(), storedApplication.getPermissions());
      assertNull(application.getImageFileId());
    } catch (FileStorageException e) {
      fail("Should not throw exception for a missing file");
    } finally {
      applicationCenterService.removeApplicationPlugin(pluginName);
    }

    // Second start with file attached
    ValueParam imagePathValueParam = new ValueParam();
    imagePathValueParam.setName("imagePath");
    imagePathValueParam.setValue("jar:/test.png");
    params.addParameter(imagePathValueParam);

    applicationPlugin = new ApplicationPlugin(params);
    applicationPlugin.setName(pluginName);
    assertNotNull(applicationPlugin.getApplication());
    assertNotNull(applicationPlugin.getImagePath());
    try {
      applicationCenterService.addApplicationPlugin(applicationPlugin);
      try {
        Application newApplication =appCenterStorage.getApplicationByTitle("title");
        newApplication.setChangedManually(true);
        appCenterStorage.updateApplication(newApplication);
      } catch (Exception e){
        // Expected
      }
      applicationCenterService.start();

      ApplicationList applicationsList = applicationCenterService.getApplicationsList(0, 0, null);
      assertNotNull(applicationsList);
      assertNotNull(applicationsList.getApplications());
      assertEquals(1, applicationsList.getApplications().size());

      Application storedApplication = applicationsList.getApplications().get(0);

      assertNotNull(storedApplication);
      assertNotNull(storedApplication.getId());
      assertEquals(application.getTitle(), storedApplication.getTitle());
      assertEquals(application.getUrl(), storedApplication.getUrl());
      assertEquals(application.getDescription(), storedApplication.getDescription());
      assertEquals(application.isActive(), storedApplication.isActive());
      assertEquals(application.isMandatory(), storedApplication.isMandatory());
      assertTrue(storedApplication.isSystem());
      assertEquals(application.getPermissions(), storedApplication.getPermissions());
      assertNull(application.getImageFileId());
    } catch (FileStorageException e) {
      e.printStackTrace();
    } finally {
      applicationCenterService.removeApplicationPlugin(pluginName);
    }

    // Third start with override turned on
    ValueParam overrideValueParam = new ValueParam();
    overrideValueParam.setName("override");
    overrideValueParam.setValue("true");
    params.addParameter(overrideValueParam);

    applicationPlugin = new ApplicationPlugin(params);
    applicationPlugin.setName(pluginName);
    assertNotNull(applicationPlugin.getApplication());
    assertNotNull(applicationPlugin.getImagePath());
    try {
      applicationCenterService.addApplicationPlugin(applicationPlugin);
      applicationCenterService.start();

      ApplicationList applicationsList = applicationCenterService.getApplicationsList(0, 0, null);
      assertNotNull(applicationsList);
      assertNotNull(applicationsList.getApplications());
      assertEquals(1, applicationsList.getApplications().size());

      Application storedApplication = applicationsList.getApplications().get(0);

      assertNotNull(storedApplication);
      assertNotNull(storedApplication.getId());
      assertEquals(application.getTitle(), storedApplication.getTitle());
      assertEquals(application.getUrl(), storedApplication.getUrl());
      assertEquals(application.getDescription(), storedApplication.getDescription());
      assertEquals(application.isActive(), storedApplication.isActive());
      assertEquals(application.isMandatory(), storedApplication.isMandatory());
      assertTrue(storedApplication.isSystem());
      assertEquals(application.getPermissions(), storedApplication.getPermissions());
      assertNotNull(application.getImageFileId());
    } finally {
      applicationCenterService.removeApplicationPlugin(pluginName);
    }

    // Test after deleting plugin from list, if the application was removed
    applicationCenterService.start();

    ApplicationList applicationsList = applicationCenterService.getApplicationsList(0, 0, null);
    assertNotNull(applicationsList);
    assertNotNull(applicationsList.getApplications());
    assertEquals(0, applicationsList.getApplications().size());
  }

  @Test
  public void testEnableDisableApplication() {
    InitParams applicationConf = new InitParams();
    ObjectParameter applicationParam = new ObjectParameter();
    applicationParam.setName("application");
    applicationParam.setObject(new Application(null,
                                               "title",
                                               "url",
                                               "",
                                               5L,
                                               0L,
                                               null,
                                               null,
                                               "description",
                                               false,
                                               true,
                                               false,
                                               false,
                                               true,
                                               ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP));
    applicationConf.addParameter(applicationParam);
    ValueParam valueParam = new ValueParam();
    valueParam.setName("override");
    valueParam.setValue("false");
    applicationConf.addParameter(valueParam);
    valueParam.setName("enabled");
    valueParam.setValue("false");
    applicationConf.addParameter(valueParam);
    valueParam.setName("imagePath");
    valueParam.setValue("/path/to/image.png");
    applicationConf.addParameter(valueParam);
    valueParam.setName("override-mode");
    valueParam.setValue("merge");
    applicationConf.addParameter(valueParam);
    ApplicationPlugin applicationPlugin = new ApplicationPlugin(applicationConf);
    applicationPlugin.setName("disabledApplication");
    applicationCenterService.addApplicationPlugin(applicationPlugin);
    applicationCenterService.start();
    ApplicationList applicationList = new ApplicationList();
    try {
      applicationList = applicationCenterService.getApplicationsList(0, 10, null);
    } catch (FileStorageException fileNotFoundException) {
      // Do nothing
    }
    assertEquals(0, applicationList.getSize());
    applicationCenterService.stop();

    // Enable the application
    valueParam = applicationConf.getValueParam("enabled");
    valueParam.setValue("true");
    applicationConf.addParameter(valueParam);
    applicationPlugin = new ApplicationPlugin(applicationConf);
    applicationPlugin.setName("enabledApplication");
    applicationCenterService.addApplicationPlugin(applicationPlugin);
    applicationCenterService.start();
    try {
      applicationList = applicationCenterService.getApplicationsList(0, 10, null);
    } catch (FileStorageException fileNotFoundException) {
      // Do nothing
    }
    assertEquals(1, applicationList.getSize());
    applicationCenterService.stop();
  }

  @Test
  public void testUpdateApplicationBySystem() {
    try {
      applicationCenterService.addApplicationPlugin(null);
      fail("Shouldn't be able to add null plugin");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      new ApplicationPlugin(null);
      fail("Shouldn't be able to add null params in plugin");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    InitParams params = new InitParams();
    try {
      new ApplicationPlugin(params);
      fail("Shouldn't be able to add null application in parameter of plugin");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    Application application = new Application(null,
                                              "title",
                                              "url",
                                              "",
                                              5L,
                                              0L,
                                              null,
                                              null,
                                              "description",
                                              false,
                                              true,
                                              false,
                                              false,
                                              true,
                                              ApplicationCenterService.DEFAULT_ADMINISTRATORS_GROUP);

    ObjectParameter applicationParam = new ObjectParameter();
    applicationParam.setName("application");
    applicationParam.setObject(application);
    params.addParameter(applicationParam);

    ApplicationPlugin applicationPlugin = new ApplicationPlugin(params);

    try {
      applicationCenterService.addApplicationPlugin(applicationPlugin);
      fail("Shouldn't be able to add plugin with null name");
    } catch (IllegalStateException e) {
      // Expected
    }

    String pluginName = "testapp";
    applicationPlugin.setName(pluginName);
    try {
      applicationCenterService.addApplicationPlugin(applicationPlugin);
      applicationCenterService.start();

      ApplicationList applicationsList = applicationCenterService.getApplicationsList(0, 0, null);
      assertNotNull(applicationsList);
      assertNotNull(applicationsList.getApplications());
      assertEquals(1, applicationsList.getApplications().size());
    } catch (FileStorageException e) {
      e.printStackTrace();
    } finally {
      applicationCenterService.removeApplicationPlugin(pluginName);
    }
    // Second start with file attached and override-mode is write
    ValueParam imagePathValueParam = new ValueParam();
    imagePathValueParam.setName("imagePath");
    imagePathValueParam.setValue("jar:/test.png");
    params.addParameter(imagePathValueParam);
    ValueParam overrideValueParam = new ValueParam();
    overrideValueParam.setName("override");
    overrideValueParam.setValue("false");
    params.addParameter(overrideValueParam);
    ValueParam overrideModeValueParam = new ValueParam();
    overrideModeValueParam.setName("override-mode");
    overrideModeValueParam.setValue("write");
    params.addParameter(overrideModeValueParam);

    applicationPlugin = new ApplicationPlugin(params);
    applicationPlugin.setName(pluginName);
    try {
      applicationCenterService.addApplicationPlugin(applicationPlugin);
      try {
        Application newApplication =appCenterStorage.getApplicationByTitle("title");
        newApplication.setChangedManually(true);
        appCenterStorage.updateApplication(newApplication);
      } catch (Exception e){
        // Expected
      }

      applicationCenterService.start();

      ApplicationList applicationsList = applicationCenterService.getApplicationsList(0, 0, null);
      //this updated done  by system because override-mode is write and isChangedManually true
      assertEquals(false,applicationsList.getApplications().get(0).isChangedManually());
    } catch (FileStorageException e) {
      e.printStackTrace();
    } finally {
      applicationCenterService.removeApplicationPlugin(pluginName);
    }
    // Third start with file attached and override-mode is merge
    ValueParam imagePathValueParam1 = new ValueParam();
    imagePathValueParam1.setName("imagePath");
    imagePathValueParam1.setValue("jar:/test.png");
    params.addParameter(imagePathValueParam1);
    ValueParam overrideValueParam1 = new ValueParam();
    overrideValueParam1.setName("override");
    overrideValueParam1.setValue("false");
    params.addParameter(overrideValueParam1);
    ValueParam overrideModeValueParam1 = new ValueParam();
    overrideModeValueParam1.setName("override-mode");
    overrideModeValueParam1.setValue("merge");
    params.addParameter(overrideModeValueParam1);

    applicationPlugin = new ApplicationPlugin(params);
    applicationPlugin.setName(pluginName);
    try {
      applicationCenterService.addApplicationPlugin(applicationPlugin);
      try {
        Application newApplication =appCenterStorage.getApplicationByTitle("title");
        newApplication.setChangedManually(true);
        appCenterStorage.updateApplication(newApplication);
      } catch (Exception e){
        // Expected
      }
      applicationCenterService.start();

      ApplicationList applicationsList = applicationCenterService.getApplicationsList(0, 0, null);
      //Ignore updating application, override mode is merge and isChangedManually is true
      assertEquals(true,applicationsList.getApplications().get(0).isChangedManually());
    } catch (FileStorageException e) {
      fail("Avatar file not found for the application");
    } finally {
      applicationCenterService.removeApplicationPlugin(pluginName);
    }
  }
}
