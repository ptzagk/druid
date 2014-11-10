/*
 * Druid - a distributed column store.
 * Copyright (C) 2012, 2013  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package io.druid.metadata;

import com.google.api.client.repackaged.com.google.common.base.Throwables;
import com.google.common.base.Supplier;
import com.google.inject.Inject;
import org.apache.derby.drda.NetworkServerControl;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.ConnectionFactory;

import java.net.InetAddress;

public class DerbyConnector extends SQLMetadataConnector
{
  private static final String SERIAL_TYPE = "BIGINT GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)";
  private final DBI dbi;

  @Inject
  public DerbyConnector(Supplier<MetadataStorageConnectorConfig> config, Supplier<MetadataStorageTablesConfig> dbTables)
  {
    this(config, dbTables, new DBI(getConnectionFactory("druidDerbyDb")));
  }

  public DerbyConnector(Supplier<MetadataStorageConnectorConfig> config, Supplier<MetadataStorageTablesConfig> dbTables, DBI dbi)
  {
    super(config, dbTables);
    this.dbi = dbi;
  }

  @Override
  protected boolean tableExists(Handle handle, String tableName)
  {
    return !handle.createQuery("select * from SYS.SYSTABLES where tablename = :tableName")
                  .bind("tableName", tableName.toUpperCase())
                  .list()
                  .isEmpty();
  }

  @Override
  protected String getSerialType()
  {
    return SERIAL_TYPE;
  }

  @Override
  public DBI getDBI() { return dbi; }

  private static ConnectionFactory getConnectionFactory(String dbName)
  {
    try {
      NetworkServerControl server = new NetworkServerControl(InetAddress.getByName("localhost"),1527);
      server.start(null);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
    return new DerbyConnectionFactory(dbName);
  }
}
