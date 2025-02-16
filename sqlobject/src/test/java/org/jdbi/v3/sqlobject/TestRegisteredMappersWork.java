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
package org.jdbi.v3.sqlobject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Something;
import org.jdbi.v3.core.mapper.NoSuchMapperException;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.mapper.SomethingMapper;
import org.jdbi.v3.core.result.ResultSetException;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.testing.junit5.JdbiExtension;
import org.jdbi.v3.testing.junit5.internal.TestingInitializers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestRegisteredMappersWork {

    @RegisterExtension
    public JdbiExtension h2Extension = JdbiExtension.h2().withInitializer(TestingInitializers.something()).withPlugin(new SqlObjectPlugin());

    public interface BooleanDao {

        @SqlQuery("select 1+1 = 2")
        boolean fetchABoolean();
    }

    @Test
    public void testFoo() {
        boolean worldIsRight = h2Extension.getSharedHandle().attach(BooleanDao.class).fetchABoolean();
        assertThat(worldIsRight).isTrue();
    }

    public static class Bean {
        private String name;
        private String color;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }
    }

    private interface BeanMappingDao extends SqlObject {
        @SqlUpdate("create table beans (name varchar primary key, color varchar)")
        void createBeanTable();

        @SqlUpdate("insert into beans (name, color) values (:name, :color)")
        void insertBean(@BindBean Bean bean);

        @SqlQuery("select name, color from beans where name = :name")
        @RegisterBeanMapper(Bean.class)
        Bean findByName(@Bind("name") String name);

        @RegisterBeanMapper(Bean.class)
        default Optional<Bean> findByNameDefaultMethod(String name) {
            return getHandle().createQuery("select name, color from beans where name = :name")
                    .bind("name", name)
                    .mapTo(Bean.class) // uses annotation-registered mapper
                    .findFirst();
        }
    }

    @Test
    public void testBeanMapperFactory() {
        BeanMappingDao bdb = h2Extension.getSharedHandle().attach(BeanMappingDao.class);
        bdb.createBeanTable();

        Bean lima = new Bean();
        lima.setColor("green");
        lima.setName("lima");

        bdb.insertBean(lima);

        Bean anotherLima = bdb.findByName("lima");
        assertThat(anotherLima.getName()).isEqualTo(lima.getName());
        assertThat(anotherLima.getColor()).isEqualTo(lima.getColor());
    }

    @Test
    public void testBeanMapperFactoryDefaultMethod() {
        BeanMappingDao bdb = h2Extension.getSharedHandle().attach(BeanMappingDao.class);
        bdb.createBeanTable();

        Bean lima = new Bean();
        lima.setColor("green");
        lima.setName("lima");

        bdb.insertBean(lima);

        assertThat(bdb.findByNameDefaultMethod("lima"))
                .hasValueSatisfying(bean -> {
                    assertThat(bean).extracting(Bean::getName, Bean::getColor)
                            .contains(lima.getName(), lima.getColor());
                });
    }

    @Test
    public void testRegistered() {
        h2Extension.getSharedHandle().registerRowMapper(new SomethingMapper());

        Spiffy s = h2Extension.getSharedHandle().attach(Spiffy.class);

        s.insert(1, "Tatu");

        Something t = s.byId(1);
        assertThat(t).isEqualTo(new Something(1, "Tatu"));
    }

    @Test
    public void testBuiltIn() {
        Spiffy s = h2Extension.getSharedHandle().attach(Spiffy.class);

        s.insert(1, "Tatu");

        assertThat(s.findNameBy(1)).isEqualTo("Tatu");
    }

    @Test
    public void testRegisterRowMapperAnnotationWorks() {
        Kabob bob = h2Extension.getJdbi().onDemand(Kabob.class);

        bob.insert(1, "Henning");
        Something henning = bob.find(1);

        assertThat(henning).isEqualTo(new Something(1, "Henning"));
    }

    @Test
    public void testNoRootRegistrations() {
        try (Handle h = h2Extension.openHandle()) {
            h.execute("insert into something (id, name) values (1, 'Henning')");
            assertThatThrownBy(() -> h.createQuery("select id, name from something where id = 1")
                .mapTo(Something.class)
                .findFirst()).isInstanceOf(NoSuchMapperException.class);
        }
    }

    @Test
    public void testNoErrorOnNoData() {
        Kabob bob = h2Extension.getJdbi().onDemand(Kabob.class);

        Something henning = bob.find(1);
        assertThat(henning).isNull();

        List<Something> rs = bob.listAll();
        assertThat(rs).isEmpty();

    }

    @Test
    public void testIteratorCloses() {
        Kabob bob = h2Extension.getJdbi().onDemand(Kabob.class);

        Iterator<Something> itty = bob.iterateAll();
        assertThatThrownBy(itty::hasNext).isInstanceOf(ResultSetException.class);
    }

    public interface Spiffy {

        @SqlQuery("select id, name from something where id = :id")
        Something byId(@Bind("id") long id);

        @SqlQuery("select name from something where id = :id")
        String findNameBy(@Bind("id") long id);

        @SqlUpdate("insert into something (id, name) values (:id, :name)")
        void insert(@Bind("id") long id, @Bind("name") String name);
    }

    @RegisterRowMapper(MySomethingMapper.class)
    public interface Kabob {
        @SqlUpdate("insert into something (id, name) values (:id, :name)")
        void insert(@Bind("id") int id, @Bind("name") String name);

        @SqlQuery("select id, name from something where id = :id")
        Something find(@Bind("id") int id);

        @SqlQuery("select id, name from something order by id")
        List<Something> listAll();

        @SqlQuery("select id, name from something order by id")
        Iterator<Something> iterateAll();
    }

    public static class MySomethingMapper implements RowMapper<Something> {
        @Override
        public Something map(ResultSet r, StatementContext ctx) throws SQLException {
            return new Something(r.getInt("id"), r.getString("name"));
        }
    }

}
