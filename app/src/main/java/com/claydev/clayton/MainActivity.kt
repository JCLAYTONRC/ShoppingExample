package com.claydev.clayton

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import com.claydev.clayton.databinding.ActivityMainBinding
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : AppCompatActivity(), OnProductListener , MainAux{

    private lateinit var mainBinding: ActivityMainBinding

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var authStateListener:FirebaseAuth.AuthStateListener

    private lateinit var adapter: ProductAdapter

    private lateinit var firestoreLinstener: ListenerRegistration

    private var productSelected: Product? = null

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        val response = IdpResponse.fromResultIntent(it.data)
        if(it.resultCode == RESULT_OK){
            val user =  FirebaseAuth.getInstance().currentUser
            if(user != null){
                Toast.makeText(this, "Bienvenido", Toast.LENGTH_SHORT).show()
            }
        } else{
            if(response == null){
                Toast.makeText(this, "Hasta pronto", Toast.LENGTH_SHORT).show()
                finish()
            }else{
                response.error?.let {
                    if(it.errorCode == ErrorCodes.NO_NETWORK){
                        Toast.makeText(this, "Sin red", Toast.LENGTH_SHORT).show()
                    } else{
                        Toast.makeText(this, "Codigo de error: ${it.errorCode}", Toast.LENGTH_SHORT).show()

                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding.root)

        configAuth()
        configRecyclerView()
        //configFirestore()
        //configFirestoreRealtime()
        configButtons()
    }

    private fun configRecyclerView() {
        adapter = ProductAdapter(mutableListOf(), this)
        mainBinding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3,
                GridLayoutManager.HORIZONTAL, false)
            adapter = this@MainActivity.adapter
        }

       /* (1..20).forEach {
            val product = Product(it.toString(), "Producto $it", "Este producto es el $it",
                "", it, it* 1.1)
            adapter.add(product)
        }*/
    }

    private fun configAuth(){

        firebaseAuth = FirebaseAuth.getInstance()
        authStateListener = FirebaseAuth.AuthStateListener {  auth ->
            if(auth.currentUser != null){
                supportActionBar?.title = auth.currentUser?.displayName
                mainBinding.llProgress.visibility = View.GONE
                mainBinding.nsvProducts.visibility = View.VISIBLE
                mainBinding.efab.show()
            }else{
                val provides = arrayListOf(AuthUI.IdpConfig.EmailBuilder().build(),
                    AuthUI.IdpConfig.GoogleBuilder().build())

                resultLauncher.launch(AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(provides)
                    .setIsSmartLockEnabled(false)
                    .build())
            }


        }

    }

    override fun onResume() {
        super.onResume()
        firebaseAuth.addAuthStateListener(authStateListener)
        configFirestoreRealtime()
    }

    override fun onPause() {
        super.onPause()
        firebaseAuth.removeAuthStateListener(authStateListener)
        firestoreLinstener.remove()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.action_sign_out -> {
                AuthUI.getInstance().signOut(this)
                    .addOnSuccessListener {
                        Toast.makeText(this,"Sesion terminada.",Toast.LENGTH_SHORT).show()
                    }
                    .addOnCompleteListener {
                        if(it.isSuccessful){
                            mainBinding.nsvProducts.visibility = View.GONE
                            mainBinding.llProgress.visibility = View.VISIBLE
                            mainBinding.efab.hide()

                        } else{
                            Toast.makeText(this,"No se pudo cerrar la sesion",Toast.LENGTH_SHORT).show()

                        }
                    }
            }
        }
        return super.onOptionsItemSelected(item)
    }
    private fun configFirestore(){
        val db = FirebaseFirestore.getInstance()

        db.collection(Constants.COLL_PRODUCTS)
            .get()
            .addOnSuccessListener { snapshots ->
                for(document in snapshots){
                    val product = document.toObject(Product::class.java)
                    product.id = document.id
                    adapter.add(product)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al consultar datos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun configFirestoreRealtime(){
        val db = FirebaseFirestore.getInstance()
        val productRef = db.collection(Constants.COLL_PRODUCTS)

        firestoreLinstener = productRef.addSnapshotListener { snapshots, error ->
            if(error != null){
                Toast.makeText(this, "Error al consultar datos", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            for(snapshot in snapshots!!.documentChanges){
                val product = snapshot.document.toObject(Product::class.java)
                product.id = snapshot.document.id
                when(snapshot.type){
                    DocumentChange.Type.ADDED -> adapter.add(product)
                    DocumentChange.Type.MODIFIED -> adapter.update(product)
                    DocumentChange.Type.REMOVED -> adapter.delete(product)
                }
            }
        }
    }

    private fun configButtons(){
        mainBinding.efab.setOnClickListener{
            productSelected = null
            AddDialogFragment().show(supportFragmentManager,AddDialogFragment::class.java.simpleName)
        }
    }
    override fun onClick(product: Product) {
        productSelected = product
        AddDialogFragment().show(supportFragmentManager,AddDialogFragment::class.java.simpleName)
    }

    override fun onLongClick(product: Product) {
        val db = FirebaseFirestore.getInstance()
        val productRef = db.collection(Constants.COLL_PRODUCTS)
        product.id?.let{
            productRef.document(it)
                .delete()
                .addOnFailureListener {
                    Toast.makeText(this, "Error al eliminar datos", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun getProductSelected(): Product? = productSelected
}