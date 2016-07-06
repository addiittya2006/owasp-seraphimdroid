package org.owasp.seraphimdroid;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.owasp.seraphimdroid.adapter.ArticleAdapter;
import org.owasp.seraphimdroid.helper.ConnectionHelper;
import org.owasp.seraphimdroid.helper.DatabaseHelper;
import org.owasp.seraphimdroid.model.Article;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public class EducateFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    private RequestQueue mRequestQueue;
    private ArrayList<Article> mArrArticle;
    private ArticleAdapter va;
    private SwipeRefreshLayout swipeRefreshLayout;
    private JsonArrayRequest jar;
    private String tags;
    private DatabaseHelper db;
    private ConnectionHelper ch;

    private static final String BASE_URL = "http://educate-seraphimdroid.rhcloud.com/";
    private static final String url = BASE_URL + "articles.json";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_educate, container, false);

        db = new DatabaseHelper(getActivity());
        ch = new ConnectionHelper(getActivity().getApplicationContext());

        Intent i = getActivity().getIntent();
        tags = i.getStringExtra("tags");

        mArrArticle = new ArrayList<>();
        va = new ArticleAdapter(mArrArticle, new ArticleAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Article item) {

                Intent i = new Intent(getActivity(), WebViewActivity.class);
                i.putExtra("id", item.getId());
                if (ch.isConnectingToInternet()) {
                    i.putExtra("url", BASE_URL + "articles/" + item.getId());
                    i.putExtra("header", "Article from " + item.getCategory() +  " Category");
                    ArrayList<Article> arr = db.getOfflineReadArticles();
                    if (!arr.isEmpty()) {
                        for (Article article: arr){
                            uploadOfflineStats(Integer.parseInt(article.getId()));
                        }
//                        db.markUploaded(arr);
                    }
                    startActivity(i);
                } else {
                    FileInputStream fis;
                    try {
                        fis = new FileInputStream(new File(getActivity().getFilesDir().getAbsolutePath() + File.separator + item.getId() + "page.mht"));
                        if (fis.read() == 0) {
                            throw new FileNotFoundException();
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        Toast.makeText(getActivity(), "NO Internet", Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(getActivity(), "Some Error Occurred.", Toast.LENGTH_SHORT).show();
                    }
                    i.putExtra("url", "file:///" + getActivity().getFilesDir().getAbsolutePath() + File.separator + item.getId() + "page.mht");
                    i.putExtra("header", "Article from " + item.getCategory() + " Category");
                    db.addOfflineRead(Integer.parseInt(item.getId()));
                    startActivity(i);
                }

            }
        });

        jar = new JsonArrayRequest(url, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                try {

                    for (int i = 0; i < response.length(); i++) {

                        JSONObject pjo = (JSONObject) response.get(i);
                        String id = pjo.getString("id");
                        String title = pjo.getString("title");
                        String text = pjo.getString("text");

                        Article article = new Article();
                        article.setId(id);
                        article.setText(text);
                        article.setTitle(title);
                        article.setCachefile(getActivity().getFilesDir().getAbsolutePath() + File.separator + id + "page.mht");

                        if (!Objects.equals(pjo.getString("category"), "null")){
                            JSONObject category = pjo.getJSONObject("category");
                            article.setCategory(category.getString("name"));
                        }
                        else{
                            article.setCategory("Other");
                        }

                        if (!Objects.equals(pjo.getString("tags"), "null")){
                            JSONArray tags = pjo.getJSONArray("tags");
                            ArrayList<String> taglist = new ArrayList<>();
                            for (int j=0; j < tags.length(); j++){
                                JSONObject tag = tags.getJSONObject(j);
                                String tag_name = tag.getString("name");
                                taglist.add(tag_name);
                            }
                            article.setTags(taglist);
                        }
                        else{
                            article.setTags(new ArrayList<String>());
                        }

                        mArrArticle.add(article);

                    }

                    db.addNewArticles(mArrArticle);

                    va.notifyDataSetChanged();

                    swipeRefreshLayout.setRefreshing(false);

                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), "Some Error Occurred.", Toast.LENGTH_SHORT).show();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if (error.getMessage() != null) {
                    Toast.makeText(getActivity(), "Please Connect to the internet", Toast.LENGTH_LONG).show();
                    mArrArticle.addAll(db.getAllArticles());
                    va.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        });

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        RecyclerView lstView = (RecyclerView) view.findViewById(R.id.recycle_articles);

//        lstView.setOnScrollListener(new RecyclerView.OnScrollListener(){
//            @Override
//            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
//                int topRowVerticalPosition =
//                        (recyclerView == null || recyclerView.getChildCount() == 0) ? 0 : recyclerView.getChildAt(0).getTop();
//                swipeRefreshLayout.setEnabled(topRowVerticalPosition >= 0);
//
//            }
//
//            @Override
//            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
//                super.onScrollStateChanged(recyclerView, newState);
//            }
//        });


        lstView.setLayoutManager(linearLayoutManager);
        lstView.setAdapter(va);

        mRequestQueue = Volley.newRequestQueue(getActivity());

        swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                swipeRefreshLayout.setRefreshing(true);
//                                        Log.i("Hello", "run: " + tags);
                if (tags != null){
                    mArrArticle.addAll(db.getArticlesWithTag(tags));
                    va.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);
                } else {
                    mRequestQueue.add(jar);
                }
            }
        });

        return view;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.feedback_educate:
                Log.d("Feedback", "onOptionsItemSelected: Feedback selected");
                Intent i = new Intent(getActivity(), FeedbackActivity.class);
                startActivity(i);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_educate, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onRefresh() {
        mArrArticle.clear();
        va.notifyDataSetChanged();
        jar = new JsonArrayRequest(url, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                try {

                    for (int i = 0; i < response.length(); i++) {

                        JSONObject pjo = (JSONObject) response.get(i);
                        String id = pjo.getString("id");
                        String title = pjo.getString("title");
                        String text = pjo.getString("text");

                        Article article = new Article();
                        article.setId(id);
                        article.setText(text);
                        article.setTitle(title);
                        article.setCachefile(getActivity().getFilesDir().getAbsolutePath() + File.separator + id + "page.mht");

                        if (!Objects.equals(pjo.getString("category"), "null")){
                            JSONObject category = pjo.getJSONObject("category");
                            article.setCategory(category.getString("name"));
                        }
                        else{
                            article.setCategory("Other");
                        }

                        if (!Objects.equals(pjo.getString("tags"), "null")){
                            JSONArray tags = pjo.getJSONArray("tags");
                            ArrayList<String> taglist = new ArrayList<>();
                            for (int j=0; j < tags.length(); j++){
                                JSONObject tag = tags.getJSONObject(j);
                                String tag_name = tag.getString("name");
                                taglist.add(tag_name);
                            }
                            article.setTags(taglist);
                        }
                        else{
                            article.setTags(new ArrayList<String>());
                        }

                        mArrArticle.add(article);

                    }

                    db.addNewArticles(mArrArticle);

                    va.notifyDataSetChanged();

                    swipeRefreshLayout.setRefreshing(false);

                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), "Some Error Occurred.", Toast.LENGTH_SHORT).show();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if (error.getMessage() != null) {
                    Toast.makeText(getActivity(), "Please Connect to the internet", Toast.LENGTH_LONG).show();
                    mArrArticle.addAll(db.getAllArticles());
                    va.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        });
        mRequestQueue.add(jar);
        tags=null;
    }

    public void uploadOfflineStats(int id) {
        final String addurl = "http://educate-seraphimdroid.rhcloud.com/articles/"+Integer.toString(id)+".json";
        int reads = db.getOfflineReads(id);
        JSONObject header = new JSONObject();
        try {
            header.put("Content-Type", "application/json");
            header.put("many", reads);
        } catch (JSONException ignored) {}

        JsonObjectRequest addUsage = new JsonObjectRequest(Request.Method.GET, addurl, header,
                new Response.Listener<JSONObject>() {
                    String resp;
                    @Override
                    public void onResponse(JSONObject response) {
                        try { resp = response.getString("id"); } catch (JSONException e) { Toast.makeText(getActivity(), "Some Error Occured.", Toast.LENGTH_SHORT).show(); }
                        if (resp != null) {
                            Log.i("Usage", "onResponse: Analyzed");
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("TAG", "onErrorResponse: "+"Error Response");
            }
        });

        RequestQueue requestQueue = Volley.newRequestQueue(getActivity());
        requestQueue.add(addUsage);
    }

}
