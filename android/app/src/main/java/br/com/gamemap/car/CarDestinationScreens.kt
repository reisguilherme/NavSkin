package br.com.gamemap.car

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import br.com.gamemap.GameMapApplication
import br.com.gamemap.R
import br.com.gamemap.navigation.model.Destination
import br.com.gamemap.navigation.storage.SavedPlace

/** Screens observe shared state only while visible; no extra location listeners or requests. */
internal abstract class ObservedNavigationScreen(context: CarContext, protected val app: GameMapApplication) : Screen(context) {
    private var unsubscribes = emptyList<() -> Unit>()
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (unsubscribes.isEmpty()) unsubscribes = listOf(
                    app.navigation.addListener { invalidate() }, app.places.addListener { invalidate() },
                    app.navigationSettings.addListener { invalidate() }, app.preferences.addListener { invalidate() },
                    app.mapConnectivity.addListener { invalidate() },
                )
            }
            override fun onStop(owner: LifecycleOwner) = release()
            override fun onDestroy(owner: LifecycleOwner) = release()
        })
    }
    private fun release() { unsubscribes.forEach { it() }; unsubscribes = emptyList() }
    protected fun selectDestination(destination: Destination) {
        screenManager.popToRoot()
        app.navigation.selectDestination(destination)
    }
}

internal abstract class PagedCarListScreen(
    context: CarContext, app: GameMapApplication, private val title: String,
) : ObservedNavigationScreen(context, app) {
    private var page = 0
    protected abstract fun rows(): List<Row>
    protected open val emptyMessage = "Nenhum local disponível."
    override fun onGetTemplate(): Template {
        val rows = rows()
        val limit = hostListLimit(carContext)
        val pages = ((rows.size + limit - 1) / limit).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        val items = ItemList.Builder().setNoItemsMessage(emptyMessage)
        rows.drop(page * limit).take(limit).forEach(items::addItem)
        return ListTemplate.Builder().setSingleList(items.build())
            .setHeader(pagedHeader(title, page, pages) { page = it; invalidate() }).build()
    }
}

internal fun pagedHeader(title: String, page: Int, pages: Int, change: (Int) -> Unit): Header {
    val builder = Header.Builder().setTitle(if (pages > 1) "$title · ${page + 1}/$pages" else title)
        .setStartHeaderAction(Action.BACK)
    if (page > 0) builder.addEndHeaderAction(Action.Builder().setTitle("Anteriores")
        .setOnClickListener { change(page - 1) }.build())
    if (page + 1 < pages) builder.addEndHeaderAction(Action.Builder().setTitle("Próximas")
        .setOnClickListener { change(page + 1) }.build())
    return builder.build()
}

internal class CarDestinationsScreen(context: CarContext, app: GameMapApplication) :
    PagedCarListScreen(context, app, "Destinos") {
    override fun rows(): List<Row> = buildList {
        val places = app.places.snapshot()
        places.savedPlaces.firstOrNull { it.id == places.homeId }?.let { add(savedShortcut("Casa", it)) }
        places.savedPlaces.firstOrNull { it.id == places.workId }?.let { add(savedShortcut("Trabalho", it)) }
        add(Row.Builder().setTitle("Locais salvos").addText("${places.savedPlaces.size} locais")
            .setBrowsable(true).setOnClickListener {
                screenManager.push(CarSavedDestinationsScreen(carContext, app))
            }.build())
        add(Row.Builder().setTitle("Destinos recentes").addText("${places.recents.size} destinos")
            .setBrowsable(true).setOnClickListener {
                screenManager.push(CarRecentDestinationsScreen(carContext, app))
            }.build())
        add(Row.Builder().setTitle("Pesquisar endereço ou lugar")
            .addText(if (app.navigation.snapshot().providerConfigured) "Digite quando estacionado ou use a busca do sistema."
                else "Configure a chave do serviço no celular.")
            .setBrowsable(true).setOnClickListener {
                screenManager.push(CarDestinationSearchScreen(carContext, app))
            }.build())
        places.resumeDestination?.let { destination ->
            add(Row.Builder().setTitle("Retomar: ${destination.name}").addText("Calcular uma nova rota deste local.")
                .setOnClickListener { screenManager.popToRoot(); app.navigation.resumeDestination() }.build())
        }
        add(Row.Builder().setTitle("GPS e permissões")
            .addText(CarNavigationModels.statusTitle(app.navigation.snapshot()))
            .setOnClickListener(ParkedOnlyOnClickListener.create { requestCarLocation(carContext, app) }).build())
        if (!app.navigation.snapshot().providerConfigured) add(Row.Builder().setTitle("Configurar no celular")
            .addText("Chave do serviço, nomes e edição de locais salvos.")
            .setOnClickListener(ParkedOnlyOnClickListener.create { openNavigationPhone(carContext) }).build())
    }
    private fun savedShortcut(title: String, saved: SavedPlace): Row = Row.Builder().setTitle(title)
        .addText(saved.name).setImage(savedCarIcon(carContext, app, saved))
        .setOnClickListener { selectDestination(saved.asDestination()) }.build()
}

internal class CarSavedDestinationsScreen(context: CarContext, app: GameMapApplication) :
    PagedCarListScreen(context, app, "Locais salvos") {
    override val emptyMessage = "Salve lugares no celular para acessá-los aqui."
    override fun rows(): List<Row> = app.places.snapshot().savedPlaces.map { place ->
        Row.Builder().setTitle(place.name)
            .addText(place.address.ifBlank { "${place.point.latitude}, ${place.point.longitude}" })
            .setImage(savedCarIcon(carContext, app, place))
            .setOnClickListener { selectDestination(place.asDestination()) }.build()
    }
}

internal class CarRecentDestinationsScreen(context: CarContext, app: GameMapApplication) :
    PagedCarListScreen(context, app, "Destinos recentes") {
    override val emptyMessage = "Os destinos iniciados aparecerão aqui."
    override fun rows(): List<Row> = app.places.snapshot().recents.map { recent ->
        destinationRow(recent.destination) { selectDestination(recent.destination) }
    }
}

internal class CarDestinationSearchScreen(
    context: CarContext, app: GameMapApplication, initialQuery: String = "",
) : ObservedNavigationScreen(context, app) {
    private val handler = Handler(Looper.getMainLooper())
    private var query = initialQuery
    private var pending: Runnable? = null
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (query.length >= 2) app.navigation.search(query)
            }
            override fun onStop(owner: LifecycleOwner) { pending?.let(handler::removeCallbacks); pending = null }
        })
    }
    override fun onGetTemplate(): Template {
        val state = app.navigation.snapshot()
        val builder = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                query = searchText.take(300)
                pending?.let(handler::removeCallbacks)
                pending = Runnable { pending = null; app.navigation.search(query) }
                    .also { handler.postDelayed(it, 400L) }
                invalidate()
            }
            override fun onSearchSubmitted(searchText: String) {
                query = searchText.take(300)
                pending?.let(handler::removeCallbacks)
                pending = null
                app.navigation.search(query)
            }
        }).setHeaderAction(Action.BACK).setInitialSearchText(query)
            .setSearchHint("Endereço ou estabelecimento")
            .setShowKeyboardByDefault(false)
        if (state.searching || pending != null) return builder.setLoading(true).build()
        val items = ItemList.Builder().setNoItemsMessage(state.searchError ?: if (query.length >= 2)
            "Nenhum resultado. Inclua cidade ou região." else "Pesquise quando estacionado ou escolha um destino recente.")
        val results = if (query.length >= 2) state.searchResults else app.places.snapshot().recents.map { it.destination }
        results.take(hostListLimit(carContext)).forEach { destination ->
            items.addItem(destinationRow(destination) { selectDestination(destination) })
        }
        return builder.setItemList(items.build()).build()
    }
}

internal fun destinationRow(destination: Destination, click: () -> Unit): Row = Row.Builder()
    .setTitle(destination.name).addText(destination.address.ifBlank {
        "${destination.point.latitude}, ${destination.point.longitude}"
    }).setOnClickListener { click() }.build()

private fun savedCarIcon(context: CarContext, app: GameMapApplication, place: SavedPlace): CarIcon =
    catalogCarIcon(context, app, app.icons.savedIcon(place.iconId, app.preferences.snapshot().theme).preview)

internal fun catalogCarIcon(context: CarContext, app: GameMapApplication, path: String): CarIcon =
    runCatching {
        val bitmap = app.assets.open(path).use(BitmapFactory::decodeStream)
            ?: error("Missing catalog preview")
        CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }.getOrElse { carIcon(context, R.drawable.phone_map) }
