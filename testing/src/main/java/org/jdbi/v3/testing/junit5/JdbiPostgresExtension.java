/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.testing.junit5;

import javax.sql.DataSource;

import de.softwareforge.testing.postgres.embedded.DatabaseInfo;
import de.softwareforge.testing.postgres.junit5.EmbeddedPgExtension;
import de.softwareforge.testing.postgres.junit5.SingleDatabaseBuilder;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Jdbi PostgreSQL JUnit 5 rule using the pg-embedded component.
 * <p>
 * Using this class with the {@code @ExtendWith} annotation is equivalent to using the {@link SingleDatabaseBuilder} to create a {@link EmbeddedPgExtension}
 * that creates a new postgres instance per test. This is slower than using the {@link de.softwareforge.testing.postgres.junit5.MultiDatabaseBuilder} and using
 * {@link JdbiExtension#postgres(EmbeddedPgExtension)}.
 * <p>
 * Override methods for special case construction:
 *
 * <pre>{@code
 *     @RegisterExtension
 *     public JdbiExtension extension = new JdbiPostgresExtension(pg) {
 *         @Override
 *         protected DataSource createDataSource() {
 *            ...
 *         }
 *     };
 * }</pre>
 * <p>
 * Use with {@link org.junit.jupiter.api.extension.ExtendWith}:
 *
 * <pre>{@code
 * @ExtendWith(JdbiPostgresExtension.class)
 * public class DatabaseTest {
 *     @Test
 *     public void testWithJdbi(Jdbi jdbi) {
 *         ...
 *     }
 *
 *     @Test
 *     public void testWithHandle(Handle handle) {
 *         ...
 *     }
 * }
 * }</pre>
 */
public class JdbiPostgresExtension extends JdbiExtension {

    private final EmbeddedPgExtension pg;
    private final boolean extendsWith;

    private volatile DatabaseInfo info;

    static final JdbiExtension instance(EmbeddedPgExtension pg) {

        return new JdbiPostgresExtension(pg);
    }

    protected JdbiPostgresExtension(EmbeddedPgExtension pg) {
        this.pg = pg;
        this.extendsWith = false;
    }

    public JdbiPostgresExtension() {
        this.pg = SingleDatabaseBuilder.instanceWithDefaults().build();
        this.extendsWith = true;
    }

    @Override
    public String getUrl() {
        return info.asJdbcUrl();
    }

    @Override
    protected DataSource createDataSource() throws Exception {
        return info.asDataSource();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {

        if (info != null) {
            throw new IllegalStateException("info has been set!");
        }

        if (this.extendsWith) {
            pg.beforeEach(context);
        }

        info = pg.createDatabaseInfo();

        super.beforeEach(context);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (info == null) {
            throw new IllegalStateException("info has been set!");
        }

        try {
            super.afterEach(context);
        } finally {
            this.info = null;

            if (this.extendsWith) {
                pg.afterEach(context);
            }
        }
    }
}
