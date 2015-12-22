/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.db.integration.delete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mule.module.db.integration.DbTestUtil.assertExpectedUpdateCount;
import static org.mule.module.db.integration.model.Planet.MARS;
import static org.mule.module.db.integration.model.Planet.VENUS;
import org.mule.api.MessagingException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.module.db.integration.AbstractDbIntegrationTestCase;
import org.mule.module.db.integration.TestDbConfig;
import org.mule.module.db.integration.model.AbstractTestDatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runners.Parameterized;

public class DeleteBulkTestCase extends AbstractDbIntegrationTestCase
{

    public DeleteBulkTestCase(String dataSourceConfigResource, AbstractTestDatabase testDatabase)
    {
        super(dataSourceConfigResource, testDatabase);
    }

    @Parameterized.Parameters
    public static List<Object[]> parameters()
    {
        return TestDbConfig.getResources();
    }

    @Override
    protected String[] getFlowConfigurationResources()
    {
        return new String[] {"integration/delete/delete-bulk-config.xml"};
    }

    @Test
    public void deletesInBulkMode() throws Exception
    {
        List<String> planetNames = new ArrayList<String>();
        planetNames.add(VENUS.getName());
        planetNames.add(MARS.getName());

        final MuleEvent responseEvent = runFlow("deleteBulk", planetNames);
        final MuleMessage response = responseEvent.getMessage();

        assertBulkDelete(response);
    }

    @Test(expected = MessagingException.class)
    public void requiresCollectionPayload() throws Exception
    {
        final MuleEvent responseEvent = runFlow("deleteBulk", TEST_MESSAGE);
        responseEvent.getMessage();
    }

    private void assertBulkDelete(MuleMessage response) throws SQLException
    {
        assertTrue(response.getPayload() instanceof int[]);
        int[] counters = (int[]) response.getPayload();
        assertEquals(2, counters.length);
        assertExpectedUpdateCount(1, counters[0]);
        assertExpectedUpdateCount(1, counters[1]);

        assertDeletedPlanetRecords(VENUS.getName());
    }
}