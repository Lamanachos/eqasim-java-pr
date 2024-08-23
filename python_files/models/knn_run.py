from sklearn.neighbors import KNeighborsRegressor
from sklearn.model_selection import train_test_split
import attributes as attrib
import pandas as pd
from sklearn.metrics import mean_squared_error
from math import sqrt
from sklearn.multioutput import MultiOutputRegressor
from statistics import mean

df_data = pd.read_csv(attrib.data_file,sep=";")
df_results = pd.read_csv(attrib.results_file,sep=";")
df_data.drop(columns=["insee"],inplace=True)
df_results.drop(columns=["insee"],inplace=True)
#df_results = df_results["er_idf"]
X = df_data.values
y = df_results.values
""" rmse_list = []
for i in range(100):
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.3)
    knn_model = MultiOutputRegressor(KNeighborsRegressor(n_neighbors=4))
    knn_model.fit(X_train, y_train)
    test_preds = knn_model.predict(X_test)
    #print(test_preds)
    #print(y_test)
    mse = mean_squared_error(y_test, test_preds)
    rmse = sqrt(mse)
    rmse_list.append(rmse)
rmse_list.append(rmse)
print(mean(rmse_list)) """