/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.compatibility.transport.file;

import static org.junit.Assert.assertThat;

import org.mule.functional.extensions.CompatibilityFunctionalTestCase;
import org.mule.runtime.core.api.client.MuleClient;

import java.io.File;

import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;

public class FileNoRecursiveConnectorTestCase extends CompatibilityFunctionalTestCase {

  @Override
  protected String getConfigFile() {
    return "file-no-recursive-connector-config.xml";
  }

  @Before
  public void setUpFile() throws Exception {
    File root = FileTestUtils.createFolder(workingDirectory.getRoot(), "root");
    File subfolder = FileTestUtils.createFolder(root, "subfolder");
    FileTestUtils.createDataFile(subfolder, TEST_MESSAGE);
  }

  @Test
  public void findsInRootDirectoryOnly() throws Exception {
    MuleClient client = muleContext.getClient();

    assertThat("Found a file from a sub directory", client.request("vm://testOut", RECEIVE_TIMEOUT).getRight().isPresent(),
               Is.is(false));
  }
}
