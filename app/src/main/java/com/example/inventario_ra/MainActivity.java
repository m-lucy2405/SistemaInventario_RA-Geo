package com.example.inventario_ra;

import android.os.Bundle;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.inventario_ra.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Uso de ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Configuración del NavController
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            
            // Vincular el BottomNavigationView con el NavController
            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);

            // Listener para ocultar el BottomNavigationView en fragmentos específicos
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (destination.getId() == R.id.nav_agregar_sucursal || destination.getId() == R.id.nav_agregar_producto) {
                    binding.bottomNavigation.setVisibility(View.GONE);
                } else {
                    binding.bottomNavigation.setVisibility(View.VISIBLE);
                }
            });
        }
    }
}
