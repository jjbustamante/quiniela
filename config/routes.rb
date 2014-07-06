Demoapp::Application.routes.draw do


  devise_for :users
 resources :users   

  #devise_for :users

  get "welcome/index"

  #config/routes.rb
  #resources :user_sessions

  #match 'login' => "user_sessions#new",      :as => :login
  #match 'logout' => " sessions#destroy", :as => :logout

  #resources :users  # give us our some normal resource routes for users
  #resource :user, :as => 'account'  # a convenience route

  resources :quinielas
  resources :matches
  resources :compare
  match 'signup' => 'users#new', :as => :signup
  match 'matches' => 'matches#show_matches', :as => :matches
  match 'quinielas/showro/:id' => 'quinielas#showro', :as => :showro
  match 'compare/compare_by_bet/:id' => 'compare#compare_by_bet', :as => :compare_by_bet

  match 'quinielas/:id/delete' => 'quinielas#delete', :as => :delete
  
  match 'results' => 'results#show_results', :as => :results
  match 'ranking' => 'ranking#show_ranking', :as => :ranking
  match 'roundOf16' => 'ranking#show_ranking2', :as => :roundOf16
  match 'firstRound' => 'ranking#show_ranking3', :as => :firstRound
  match 'update' => 'users#edit_info', :as => :update
  match 'edit_matches' => 'matches#edit', :as => :edit_matches 
  match 'brackets' => 'quinielas#brackets', :as => :brackets 
  match 'edit' => 'users#edit', :as => :edit 
  match 'showcount' => 'users#show_count', :as => :showcount
  match 'sudocool' => 'users#sudocool'
  
  root :to => 'welcome#index'
  
  # The priority is based upon order of creation:
  # first created -> highest priority.

  # Sample of regular route:
  #   match 'products/:id' => 'catalog#view'
  # Keep in mind you can assign values other than :controller and :action

  # Sample of named route:
  #   match 'products/:id/purchase' => 'catalog#purchase', :as => :purchase
  # This route can be invoked with purchase_url(:id => product.id)

  # Sample resource route (maps HTTP verbs to controller actions automatically):
  #   resources :products

  # Sample resource route with options:
  #   resources :products do
  #     member do
  #       get 'short'
  #       post 'toggle'
  #     end
  #
  #     collection do
  #       get 'sold'
  #     end
  #   end

  # Sample resource route with sub-resources:
  #   resources :products do
  #     resources :comments, :sales
  #     resource :seller
  #   end

  # Sample resource route with more complex sub-resources
  #   resources :products do
  #     resources :comments
  #     resources :sales do
  #       get 'recent', :on => :collection
  #     end
  #   end

  # Sample resource route within a namespace:
  #   namespace :admin do
  #     # Directs /admin/products/* to Admin::ProductsController
  #     # (app/controllers/admin/products_controller.rb)
  #     resources :products
  #   end

  # You can have the root of your site routed with "root"
  # just remember to delete public/index.html.
  # root :to => 'welcome#index'

  # See how all your routes lay out with "rake routes"

  # This is a legacy wild controller route that's not recommended for RESTful applications.
  # Note: This route will make all actions in every controller accessible via GET requests.
  # match ':controller(/:action(/:id))(.:format)'
end
