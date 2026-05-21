package com.example.inventario_ra.ui.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.inventario_ra.R;
import com.example.inventario_ra.databinding.ItemProductoBinding;
import com.example.inventario_ra.models.Productos;

import java.util.List;
import java.util.Locale;

public class ProductoAdapter extends RecyclerView.Adapter<ProductoAdapter.ProductoViewHolder> {

    private final List<Productos> listaProductos;
    private final OnProductoClickListener listener;
    private final OnProductoLongClickListener longListener;

    public interface OnProductoClickListener {
        void onProductoClick(Productos producto);
    }

    public interface OnProductoLongClickListener {
        void onProductoLongClick(Productos producto);
    }

    public ProductoAdapter(List<Productos> listaProductos, OnProductoClickListener listener, OnProductoLongClickListener longListener) {
        this.listaProductos = listaProductos;
        this.listener = listener;
        this.longListener = longListener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void actualizarLista(List<Productos> nuevaLista) {
        this.listaProductos.clear();
        if (nuevaLista != null) {
            this.listaProductos.addAll(nuevaLista);
        }
        notifyDataSetChanged(); // Le avisa al RecyclerView que debe volver a pintar la pantalla
    }

    @NonNull
    @Override
    public ProductoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductoBinding binding = ItemProductoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ProductoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductoViewHolder holder, int position) {
        Productos producto = listaProductos.get(position);
        holder.bind(producto, listener, longListener);
    }

    @Override
    public int getItemCount() {
        return listaProductos != null ? listaProductos.size() : 0;
    }

    public static class ProductoViewHolder extends RecyclerView.ViewHolder {
        private final ItemProductoBinding binding;

        public ProductoViewHolder(@NonNull ItemProductoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(final Productos producto, final OnProductoClickListener listener, final OnProductoLongClickListener longListener) {
            binding.tvNombre.setText(producto.getNombre());
            binding.tvPrecio.setText(String.format(Locale.getDefault(), "$ %.2f", producto.getPrecio()));
            binding.tvStock.setText(String.format(Locale.getDefault(), "Stock: %d unidades", producto.getStock()));

            // Lógica UX: Mostrar el icono de AR solo si el producto tiene URL 3D
            if (producto.getModelo_3d_url() != null && !producto.getModelo_3d_url().isEmpty()) {
                binding.imgArBadge.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.imgArBadge.setVisibility(android.view.View.GONE);
            }

            // Carga de imagen con Glide
            Glide.with(binding.imgProducto.getContext())
                    .load(producto.getImagen_ref_url())
                    .placeholder(android.R.drawable.ic_menu_gallery) // Placeholder por defecto
                    .error(android.R.drawable.ic_dialog_alert)      // Icono de error
                    .into(binding.imgProducto);

            // Listener de clic
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProductoClick(producto);
                }
            });

            // Listener de clic largo (Editar/Eliminar)
            binding.getRoot().setOnLongClickListener(v -> {
                if (longListener != null) {
                    longListener.onProductoLongClick(producto);
                    return true;
                }
                return false;
            });
        }
    }
}
