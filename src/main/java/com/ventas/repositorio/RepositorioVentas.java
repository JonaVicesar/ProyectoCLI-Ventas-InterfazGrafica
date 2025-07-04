package com.ventas.repositorio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.ventas.modelo.Cliente;
import com.ventas.modelo.Producto;
import com.ventas.modelo.Venta;
import com.ventas.util.PathManager;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Repositorio encargado de gestionar las ventas realizadas Almacena las ventas
 * en la memoria y tiene metodos para crear, editar, eliminar y consultar ventas
 */
public class RepositorioVentas {

    private static final String FILE_NAME = "ventas.json";
    private final Map<Integer, Venta> repositorio = new HashMap<>();
    private final Gson gson;
    private final String filePath;
    private int nextId = 1;;

    // El ID aumenta para cada venta
    private static int idVenta = 1;

    public RepositorioVentas() {
        this.filePath = PathManager.getDataPath(FILE_NAME);
        this.gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .registerTypeAdapter(new TypeToken<HashMap<Producto, Integer>>(){}.getType(), new ProductoMapAdapter())
            .setPrettyPrinting()
            .create();  
        cargarDatos();
    }
    
    
        
    // Adaptador para HashMap<Producto, Integer>
    private static class ProductoMapAdapter implements JsonSerializer<HashMap<Producto, Integer>>, 
                                                      JsonDeserializer<HashMap<Producto, Integer>> {
        
        @Override
        public JsonElement serialize(HashMap<Producto, Integer> src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray array = new JsonArray();
            for (Map.Entry<Producto, Integer> entry : src.entrySet()) {
                JsonObject item = new JsonObject();
                item.add("producto", context.serialize(entry.getKey()));
                item.addProperty("cantidad", entry.getValue());
                array.add(item);
            }
            return array;
        }
        
        @Override
        public HashMap<Producto, Integer> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
                throws JsonParseException {
            HashMap<Producto, Integer> map = new HashMap<>();
            JsonArray array = json.getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                Producto producto = context.deserialize(obj.get("producto"), Producto.class);
                int cantidad = obj.get("cantidad").getAsInt();
                map.put(producto, cantidad);
            }
            return map;
        }
    }

    private static class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        @Override
        public JsonElement serialize(LocalDate date, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(date.toString());
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            return LocalDate.parse(json.getAsString());
        }
    }
    
    
    private void cargarDatos() {
        try (FileReader reader = new FileReader(filePath)) {
            Type listType = new TypeToken<ArrayList<Venta>>(){}.getType();
            List<Venta> ventas = gson.fromJson(reader, listType);
            
            if (ventas != null) {
                int maxId = 0;
                for (Venta venta : ventas) {
                    repositorio.put(venta.getIdVenta(), venta);
                    if (venta.getIdVenta() > maxId) {
                        maxId = venta.getIdVenta();
                    }
                }
                nextId = maxId + 1;
            }
        } catch (IOException e) {
            System.out.println("Iniciando nuevo repositorio de ventas: " + e.getMessage());
        }
    }
    
    public void guardarDatos() {
        try (FileWriter writer = new FileWriter(filePath)) {
            List<Venta> ventas = new ArrayList<>(repositorio.values());
            gson.toJson(ventas, writer);
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar ventas: " + e.getMessage(), e);
        }
    }

    /**
     * Crea una nueva venta con un cliente, una lista de compras y una fecha
     * determinada
     *
     * @param cliente Cliente que realiza la compra
     * @param listaCompras Lista de productos con sus cantidades
     * @param fecha Fecha de la venta
     * @return ID asignado a la nueva venta
     */
    public int crearVenta(Cliente cliente, HashMap<Producto, Integer> productos, LocalDate fecha) {
        Venta venta = new Venta(nextId, cliente, productos, fecha);
        repositorio.put(nextId, venta);
        guardarDatos();
        return nextId++;
    }
    
        public boolean anularVenta(int idVenta) {
        Venta venta = repositorio.get(idVenta);
        if (venta != null) {
            venta.setAnulada(true);
            guardarDatos();
            return true;
        }
        return false;
    }
        
    /**
     * Agrega un producto a una venta existente, sumando la cantidad si ya
     * estaba presente
     *
     * @param idVenta ID de la venta
     * @param producto Producto a agregar
     * @param cantidad Cantidad a agregar
     * @return true si se agregó correctamente
     */
    public boolean agregarProducto(int idVenta, Producto producto, int cantidad) {
        Venta venta = repositorio.get(idVenta);
        if (venta == null || producto == null || cantidad <= 0) {
            return false;
        }

        HashMap<Producto, Integer> listaCompras = venta.getListaCompras();
        listaCompras.put(producto, listaCompras.getOrDefault(producto, 0) + cantidad);
        venta.calcularTotal();
        return true;
    }

    /**
     * Disminuye la cantidad de un producto en una venta. Si la cantidad queda
     * en cero o menos, el producto se elimina de la venta.
     *
     * @param idVenta ID de la venta
     * @param producto Producto a modificar
     * @param cantidad Cantidad a disminuir (> 0)
     * @return true si se modificó correctamente
     */
    public boolean disminuirCantidadProducto(int idVenta, Producto producto, int cantidad) {
        Venta venta = repositorio.get(idVenta);
        if (venta == null || producto == null || cantidad <= 0) {
            return false;
        }

        HashMap<Producto, Integer> listaCompras = venta.getListaCompras();
        if (!listaCompras.containsKey(producto)) {
            return false;
        }

        int cantidadActual = listaCompras.get(producto);
        if (cantidadActual <= cantidad) {
            listaCompras.remove(producto);
        } else {
            listaCompras.put(producto, cantidadActual - cantidad);
        }
        venta.calcularTotal(); // Recalcula el total tras el cambio
        return true;
    }

    /**
     * Elimina un producto de una venta
     *
     * @param idVenta ID de la venta
     * @param producto Producto a eliminar
     * @return true si fue eliminado correctamente
     */
    public boolean eliminarProducto(int idVenta, Producto producto) {
        Venta venta = repositorio.get(idVenta);
        if (venta == null || producto == null) {
            return false;
        }

        HashMap<Producto, Integer> listaCompras = venta.getListaCompras();
        if (listaCompras.remove(producto) != null) {
            venta.calcularTotal();
            return true;
        }
        return false;
    }

    /**
     * Elimina una venta del repositorio
     *
     * @param idVenta ID de la venta a eliminar
     * @return true si fue eliminada correctamente
     */
    public boolean eliminarVenta(int idVenta) {
        return repositorio.remove(idVenta) != null;
    }

    /**
     * Devuelve una lista de las ventas realizadas entre dos fechas
     *
     * @param desde Fecha de inicio
     * @param hasta Fecha de fin
     * @return Lista de ventas dentro del rango de fechas
     */
    public List<Venta> obtenerVentasEntreFechas(LocalDate desde, LocalDate hasta) {
        return repositorio.values().stream()
                .filter(venta -> !venta.getFecha().isBefore(desde) && !venta.getFecha().isAfter(hasta))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene una venta específica según su ID
     *
     * @param idVenta ID de la venta
     * @return Objeto Venta si existe, null en caso contrario
     */
    public Venta obtenerVentaPorId(int idVenta) {
        return repositorio.get(idVenta);
    }

    /**
     * Devuelve una lista con todas las ventas registradas
     *
     * @return Lista de ventas
     */
    public List<Venta> listarVentas() {
        return new ArrayList<>(repositorio.values());
    }

    public List<Venta> listarVentasAnuladas() {
        return repositorio.values().stream()
                .filter(Venta::isAnulada)
                .collect(Collectors.toList());
    }

    public List<Venta> filtrarPorFecha(LocalDate fecha) {
        return repositorio.values().stream()
                .filter(v -> v.getFecha().equals(fecha))
                .collect(Collectors.toList());
    }

    public List<Venta> filtrarPorCliente(int documentoCliente) {
        return repositorio.values().stream()
                .filter(v -> v.getCliente().getDocumento() == documentoCliente)
                .collect(Collectors.toList());
    }

    /**
     * Genera un informe de productos vendidos y sus cantidades entre dos fechas
     *
     * @param desde Fecha de inicio
     * @param hasta Fecha de fin
     * @return Mapa con productos como clave y cantidad total vendida como valor
     */
    public Map<Producto, Integer> generarInformeProductosVendidos(LocalDate desde, LocalDate hasta) {
        Map<Producto, Integer> productosVendidos = new HashMap<>();

        obtenerVentasEntreFechas(desde, hasta).forEach(venta -> {
            venta.getListaCompras().forEach((producto, cantidad) -> {
                productosVendidos.put(producto, productosVendidos.getOrDefault(producto, 0) + cantidad);
            });
        });

        return productosVendidos;
    }
    
    public int cantidadVentas(){
        return repositorio.size();
    }
    
    
}
