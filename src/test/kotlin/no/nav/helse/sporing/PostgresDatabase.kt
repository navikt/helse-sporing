import com.github.navikt.tbd_libs.test_support.CleanupStrategy
import com.github.navikt.tbd_libs.test_support.DatabaseContainers
import com.github.navikt.tbd_libs.test_support.InitStrategy
import java.sql.Connection

val databaseContainer = DatabaseContainers.container("sporing", CleanupStrategy.tables("arsak,tilstandsendring,vedtaksperiode_tilstandsendring"), Init())
private class Init : InitStrategy {
    override fun init(connection: Connection) {
        connection.createStatement().use {
            it.execute("create user cloudsqliamuser;")
        }
    }
}