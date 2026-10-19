Lists::Application.routes.draw do
  get "up", to: "rails/health#show", as: :rails_health_check
  devise_for :users, controllers: { omniauth_callbacks: "users/omniauth_callbacks" }
  devise_scope :user do
    get "sign_in", to: "pages#home", as: :new_user_session
    delete "sign_out", to: "devise/sessions#destroy", as: :destroy_user_session
  end
  root "pages#home"
  get "dashboard", to: "tasks#index"
  resources :tasks, only: [:create, :update, :destroy] do
    patch :star, on: :member
  end
  resource :profile, only: [:show, :update]
  resources :tags, only: [:create, :update, :destroy] do
    patch :reorder, on: :collection
  end
end
