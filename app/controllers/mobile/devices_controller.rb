module Mobile
  class DevicesController < BaseController
    def destroy
      @device.destroy!
      head :no_content
    end
  end
end
