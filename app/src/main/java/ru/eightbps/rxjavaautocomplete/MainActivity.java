package ru.eightbps.rxjavaautocomplete;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.jakewharton.rxbinding.widget.AdapterViewItemClickEvent;
import com.jakewharton.rxbinding.widget.RxAutoCompleteTextView;
import com.jakewharton.rxbinding.widget.RxTextView;
import com.jakewharton.rxbinding.widget.TextViewTextChangeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ru.eightbps.rxjavaautocomplete.data.model.Location;
import ru.eightbps.rxjavaautocomplete.data.model.PlaceAutocompleteResult;
import ru.eightbps.rxjavaautocomplete.data.model.PlaceDetailsResult;
import ru.eightbps.rxjavaautocomplete.data.model.Prediction;
import ru.eightbps.rxjavaautocomplete.data.RestClient;
import ru.eightbps.rxjavaautocomplete.utils.KeyboardHelper;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class MainActivity extends AppCompatActivity  {

    private static final long DELAY_IN_MILLIS = 500;
    private CompositeSubscription compositeSubscription = new CompositeSubscription();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final AutoCompleteTextView autoCompleteTextView = findViewById(R.id.autocomplete_text);
        addOnAutoCompleteTextViewTextChangedObserver(autoCompleteTextView);
    }

    // TODO: 1. Put the Observable<PlaceAutocompleteResult> in the custom control
    // 2. Put the observer into the control
    // the only thing the main activity needs is compositeSubscription subscribe and unsubscribe.. maybe

    private void addOnAutoCompleteTextViewTextChangedObserver(final AutoCompleteTextView autoCompleteTextView) {
        Observable<PlaceAutocompleteResult> autocompleteResponseObservable =
                RxTextView.textChangeEvents(autoCompleteTextView)
                        .debounce(DELAY_IN_MILLIS, TimeUnit.MILLISECONDS)
                        .map(new Func1<TextViewTextChangeEvent, String>() {
                            @Override
                            public String call(TextViewTextChangeEvent textViewTextChangeEvent) {
                                return textViewTextChangeEvent.text().toString();
                            }
                        })
                        .filter(new Func1<String, Boolean>() {
                            @Override
                            public Boolean call(String s) {
                                return s.length() >= 2;
                            }
                        })
                        .observeOn(Schedulers.io())
                        .flatMap(new Func1<String, Observable<PlaceAutocompleteResult>>() {
                            @Override
                            public Observable<PlaceAutocompleteResult> call(String s) {
                                return RestClient.INSTANCE.getGooglePlacesClient().autocomplete(s);
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry();

        Observer<PlaceAutocompleteResult> placeAutocompleteResultObserver = new Observer<PlaceAutocompleteResult>() {

            private static final String TAG = "PlaceAutocompleteResult";

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted");
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "onError", e);
            }

            @Override
            public void onNext(PlaceAutocompleteResult placeAutocompleteResult) {
                Log.i(TAG, placeAutocompleteResult.toString());

                List<NameAndPlaceId> list = new ArrayList<>();
                for (Prediction prediction : placeAutocompleteResult.predictions) {
                    list.add(new NameAndPlaceId(prediction.description, prediction.placeId));
                }

                ArrayAdapter<NameAndPlaceId> itemsAdapter = new ArrayAdapter<>(MainActivity.this,
                        android.R.layout.simple_list_item_1, list);
                autoCompleteTextView.setAdapter(itemsAdapter);
                String enteredText = autoCompleteTextView.getText().toString();
                if (list.size() >= 1 && enteredText.equals(list.get(0).name)) {
                    autoCompleteTextView.dismissDropDown();
                } else {
                    autoCompleteTextView.showDropDown();
                }
            }
        };
        compositeSubscription.add(autocompleteResponseObservable
                .subscribe(placeAutocompleteResultObserver));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeSubscription.unsubscribe();
    }

    private static class NameAndPlaceId {
        final String name;
        final String placeId;

        NameAndPlaceId(String name, String placeId) {
            this.name = name;
            this.placeId = placeId;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
